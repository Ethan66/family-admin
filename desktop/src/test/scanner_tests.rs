use std::fs;

use tempfile::tempdir;

use crate::scanner::{ResourceKind, scan_roots};

#[test]
fn reads_photo_dimensions_from_png_header() {
    let temp_dir = tempdir().expect("create temp directory");
    let path = temp_dir.path().join("portrait.png");
    let png_header = b"\x89PNG\r\n\x1a\n\0\0\0\x0dIHDR\0\0\0\x64\0\0\0\xc8\x08\x02\0\0\0";
    fs::write(&path, png_header).expect("write png header");

    let result = scan_roots(ResourceKind::Photo, &[temp_dir.path().to_path_buf()]);

    assert_eq!(result.resources.len(), 1);
    assert_eq!(result.resources[0].image_width, Some(100));
    assert_eq!(result.resources[0].image_height, Some(200));
}

#[test]
fn reads_exif_original_capture_time_for_photo() {
    let temp_dir = tempdir().expect("create temp directory");
    let path = temp_dir.path().join("capture.jpg");
    let mut tiff = Vec::new();
    tiff.extend_from_slice(b"II\x2a\0\x08\0\0\0");
    tiff.extend_from_slice(b"\x01\0\x69\x87\x04\0\x01\0\0\0\x1a\0\0\0\0\0\0\0");
    tiff.extend_from_slice(b"\x01\0\x03\x90\x02\0\x14\0\0\0\x2c\0\0\0\0\0\0\0");
    tiff.extend_from_slice(b"2026:10:12 09:45:32\0");
    let mut jpeg = vec![0xff, 0xd8, 0xff, 0xe1];
    jpeg.extend_from_slice(&((tiff.len() + 8) as u16).to_be_bytes());
    jpeg.extend_from_slice(b"Exif\0\0");
    jpeg.extend_from_slice(&tiff);
    jpeg.extend_from_slice(&[0xff, 0xd9]);
    fs::write(&path, jpeg).expect("write exif photo");

    let result = scan_roots(ResourceKind::Photo, &[temp_dir.path().to_path_buf()]);

    assert_eq!(result.resources.len(), 1);
    assert_eq!(
        result.resources[0].captured_at.as_deref(),
        Some("2026-10-12T09:45:32")
    );
}

#[test]
fn photo_without_exif_keeps_capture_time_empty() {
    let temp_dir = tempdir().expect("create temp directory");
    fs::write(temp_dir.path().join("plain.jpg"), b"no exif").expect("write photo");

    let result = scan_roots(ResourceKind::Photo, &[temp_dir.path().to_path_buf()]);

    assert_eq!(result.resources.len(), 1);
    assert!(result.resources[0].captured_at.is_none());
}

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
