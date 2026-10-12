use std::fs;

use tempfile::tempdir;

use crate::scanner::{ResourceKind, scan_roots};

#[test]
fn scans_supported_files_recursively_and_ignores_other_extensions() {
    let temp_dir = tempdir().expect("create temp directory");
    let nested = temp_dir.path().join("nested");
    fs::create_dir(&nested).expect("create nested directory");
    fs::write(temp_dir.path().join("cover.JPG"), b"image").expect("write image");
    fs::write(nested.join("memory.webp"), b"image").expect("write nested image");
    fs::write(nested.join("movie.mp4"), b"movie").expect("write unsupported resource");

    let result = scan_roots(ResourceKind::Photo, &[temp_dir.path().to_path_buf()]);

    assert_eq!(result.resources.len(), 2);
    assert!(
        result
            .resources
            .iter()
            .any(|resource| resource.name == "cover.JPG")
    );
    assert!(
        result
            .resources
            .iter()
            .any(|resource| resource.name == "memory.webp")
    );
    assert!(result.warnings.is_empty());
}

#[test]
fn records_missing_roots_as_warnings_without_failing_the_scan() {
    let temp_dir = tempdir().expect("create temp directory");
    let missing = temp_dir.path().join("missing");

    let result = scan_roots(ResourceKind::Movie, std::slice::from_ref(&missing));

    assert!(result.resources.is_empty());
    assert_eq!(result.warnings.len(), 1);
    assert_eq!(result.warnings[0].path, missing.to_string_lossy());
}

#[test]
fn records_all_photo_directories_even_when_they_are_empty() {
    let temp_dir = tempdir().expect("create temp directory");
    let empty = temp_dir.path().join("empty");
    let child = temp_dir.path().join("nested").join("child");
    fs::create_dir_all(&empty).expect("create empty directory");
    fs::create_dir_all(&child).expect("create nested directory");
    fs::write(child.join("photo.jpg"), b"photo").expect("write photo");

    let result = scan_roots(ResourceKind::Photo, &[temp_dir.path().to_path_buf()]);

    assert_eq!(result.directories.len(), 4);
    for directory in [
        temp_dir.path(),
        empty.as_path(),
        child.parent().unwrap(),
        child.as_path(),
    ] {
        assert!(
            result
                .directories
                .contains(&directory.canonicalize().unwrap())
        );
    }
}
