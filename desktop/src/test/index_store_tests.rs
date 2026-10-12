use std::path::PathBuf;

use crate::index_store::{
    ExamMetadata, PhotoMetadata, ResourceIndex, ResourceMetadata, find_photo_for_deletion,
    merge_scan_result, move_photos, persist_resource_metadata, remove_deleted_photo,
};
use crate::scanner::{ResourceEntry, ResourceKind, ScanResult, scan_roots};

fn entry(id: &str, kind: ResourceKind) -> ResourceEntry {
    ResourceEntry {
        id: id.to_owned(),
        kind,
        path: PathBuf::from(format!("C:\\resources\\{id}")),
        name: id.to_owned(),
        extension: ".jpg".to_owned(),
        size: 10,
        modified_at: "2026-09-24T10:00:00Z".to_owned(),
    }
}

#[test]
fn replaces_only_the_rescanned_resource_kind() {
    let mut index = ResourceIndex::empty(12);
    index.resources = vec![
        entry("old-photo", ResourceKind::Photo),
        entry("movie", ResourceKind::Movie),
    ];
    let result = ScanResult {
        resources: vec![entry("new-photo", ResourceKind::Photo)],
        directories: vec![],
        warnings: vec![],
    };

    merge_scan_result(
        &mut index,
        ResourceKind::Photo,
        vec![PathBuf::from("C:\\photos")],
        result,
    );

    assert!(
        index
            .resources
            .iter()
            .any(|resource| resource.id == "new-photo")
    );
    assert!(
        index
            .resources
            .iter()
            .any(|resource| resource.id == "movie")
    );
    assert!(
        !index
            .resources
            .iter()
            .any(|resource| resource.id == "old-photo")
    );
}

#[test]
fn keeps_manual_metadata_when_scanning_and_serializing_index() {
    let mut index: ResourceIndex = serde_json::from_value(serde_json::json!({
        "schemaVersion": 2,
        "familyId": 12,
        "roots": {"photos": [], "movies": [], "notes": [], "exams": []},
        "lastScannedAt": null,
        "resources": [],
        "warnings": [],
        "metadataById": {
            "photo-1": {"kind": "PHOTO", "details": {
                "title": "全家福", "album": "家人", "date": "2026-10-04", "description": "周末"
            }}
        }
    }))
    .unwrap();
    merge_scan_result(
        &mut index,
        ResourceKind::Photo,
        vec![],
        ScanResult {
            resources: vec![entry("photo-1", ResourceKind::Photo)],
            directories: vec![],
            warnings: vec![],
        },
    );
    let saved = serde_json::to_value(&index).unwrap();
    assert_eq!(
        saved["metadataById"]["photo-1"]["details"]["title"],
        "全家福"
    );
    assert_eq!(saved["schemaVersion"], 2);
    assert!(index.photo_directories.is_empty());
}

#[test]
fn photo_categories_and_rating_survive_json_and_old_index_defaults() {
    let old: ResourceMetadata = serde_json::from_value(serde_json::json!({
        "kind": "PHOTO",
        "details": {"title": "旧照", "album": "家人", "date": "", "description": ""}
    }))
    .unwrap();
    let old_json = serde_json::to_value(old).unwrap();
    assert_eq!(old_json["details"]["categories"], serde_json::json!([]));
    assert!(old_json["details"]["rating"].is_null());

    let new: ResourceMetadata = serde_json::from_value(serde_json::json!({
        "kind": "PHOTO",
        "details": {"title": "新照", "album": "", "date": "", "description": "", "categories": ["旅行", "家人"], "rating": 4}
    }))
    .unwrap();
    assert!(new.validate_for(ResourceKind::Photo).is_ok());
    let new_json = serde_json::to_value(new).unwrap();
    assert_eq!(
        new_json["details"]["categories"],
        serde_json::json!(["旅行", "家人"])
    );
    assert_eq!(new_json["details"]["rating"], 4);
}

#[test]
fn photo_rating_and_categories_are_validated() {
    let invalid_rating: ResourceMetadata = serde_json::from_value(serde_json::json!({
        "kind": "PHOTO",
        "details": {"title": "新照", "album": "", "date": "", "description": "", "categories": ["旅行"], "rating": 6}
    }))
    .unwrap();
    assert!(invalid_rating.validate_for(ResourceKind::Photo).is_err());
    let repeated_category: ResourceMetadata = serde_json::from_value(serde_json::json!({
        "kind": "PHOTO",
        "details": {"title": "新照", "album": "", "date": "", "description": "", "categories": ["旅行", "旅行"], "rating": 5}
    }))
    .unwrap();
    assert!(repeated_category.validate_for(ResourceKind::Photo).is_err());
}

#[test]
fn stores_photo_directories_without_losing_them_on_other_resource_scans() {
    let mut index = ResourceIndex::empty(12);
    let photo_root = PathBuf::from("C:\\photos");
    let empty_folder = photo_root.join("empty");
    merge_scan_result(
        &mut index,
        ResourceKind::Photo,
        vec![photo_root.clone()],
        ScanResult {
            resources: vec![],
            directories: vec![photo_root, empty_folder.clone()],
            warnings: vec![],
        },
    );
    assert!(index.photo_directories.contains(&empty_folder));

    merge_scan_result(
        &mut index,
        ResourceKind::Movie,
        vec![PathBuf::from("C:\\movies")],
        ScanResult {
            resources: vec![],
            directories: vec![],
            warnings: vec![],
        },
    );
    assert!(index.photo_directories.contains(&empty_folder));
}

#[test]
fn rejects_metadata_for_a_different_kind_and_invalid_scores() {
    let metadata = ResourceMetadata::Exam(ExamMetadata {
        title: "数学期中".to_owned(),
        subject: "数学".to_owned(),
        grade: "初二".to_owned(),
        date: "2026-10-04".to_owned(),
        full_score: 100,
        score: Some(110.0),
        status: "已批改".to_owned(),
        note: String::new(),
    });
    assert!(metadata.validate_for(ResourceKind::Movie).is_err());
    assert!(metadata.validate_for(ResourceKind::Exam).is_err());
}

#[test]
fn allows_deleting_only_indexed_photos_inside_the_registered_root() {
    let directory = tempfile::tempdir().unwrap();
    let photo_path = directory.path().join("family.jpg");
    std::fs::write(&photo_path, b"photo").unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(directory.path().to_path_buf());
    let mut photo = entry("photo-1", ResourceKind::Photo);
    photo.path = photo_path.canonicalize().unwrap();
    index.resources.push(photo.clone());

    assert_eq!(
        find_photo_for_deletion(&index, "photo-1").unwrap().path,
        photo.path
    );
    assert!(find_photo_for_deletion(&index, "missing").is_err());

    index.roots.photos.clear();
    assert!(find_photo_for_deletion(&index, "photo-1").is_err());
    index.resources[0].kind = ResourceKind::Exam;
    assert!(find_photo_for_deletion(&index, "photo-1").is_err());
}

#[test]
fn removes_only_the_deleted_photo_and_its_metadata() {
    let mut index = ResourceIndex::empty(12);
    index.resources = vec![
        entry("photo-1", ResourceKind::Photo),
        entry("movie-1", ResourceKind::Movie),
    ];
    index.metadata_by_id.insert(
        "photo-1".to_owned(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "全家福".to_owned(),
            album: "家人".to_owned(),
            date: "2026-10-09".to_owned(),
            description: String::new(),
            categories: vec![],
            rating: None,
        }),
    );

    remove_deleted_photo(&mut index, "photo-1");

    assert_eq!(index.resources.len(), 1);
    assert_eq!(index.resources[0].id, "movie-1");
    assert!(!index.metadata_by_id.contains_key("photo-1"));
}

fn photo_metadata(title: &str, album: &str) -> ResourceMetadata {
    ResourceMetadata::Photo(PhotoMetadata {
        title: title.to_owned(),
        album: album.to_owned(),
        date: "2026-10-09".to_owned(),
        description: String::new(),
        categories: vec![],
        rating: None,
    })
}

fn indexed_photo(directory: &std::path::Path, file_name: &str) -> (ResourceIndex, ResourceEntry) {
    let path = directory.join(file_name);
    std::fs::write(&path, b"photo").unwrap();
    let resource = scan_roots(ResourceKind::Photo, &[directory.to_path_buf()])
        .resources
        .into_iter()
        .find(|entry| entry.path == path.canonicalize().unwrap())
        .unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(directory.to_path_buf());
    index.resources.push(resource.clone());
    (index, resource)
}

#[test]
fn renames_photo_and_migrates_its_index_and_metadata_when_title_changes() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");
    index
        .metadata_by_id
        .insert(resource.id.clone(), photo_metadata("IMG_001", "家人"));
    let mut saved = false;

    persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("生日合照", "旅行"),
        |updated| {
            saved = true;
            assert_eq!(updated.resources[0].name, "生日合照.jpg");
            Ok(())
        },
    )
    .unwrap();

    assert!(saved);
    assert!(!directory.path().join("IMG_001.jpg").exists());
    assert!(directory.path().join("生日合照.jpg").exists());
    let rescanned = scan_roots(ResourceKind::Photo, &[directory.path().to_path_buf()]);
    assert_eq!(index.resources[0].id, rescanned.resources[0].id);
    assert_eq!(index.resources[0].path, rescanned.resources[0].path);
    assert!(!index.metadata_by_id.contains_key(&resource.id));
    assert!(index.metadata_by_id.contains_key(&index.resources[0].id));
}

#[test]
fn keeps_original_file_when_photo_title_is_unchanged() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");
    index
        .metadata_by_id
        .insert(resource.id.clone(), photo_metadata("自定义标题", "家人"));

    persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("自定义标题", "旅行"),
        |_| Ok(()),
    )
    .unwrap();

    assert!(directory.path().join("IMG_001.jpg").exists());
    assert!(!directory.path().join("自定义标题.jpg").exists());
    assert_eq!(index.resources[0].id, resource.id);
    assert_eq!(index.resources[0].path, resource.path);
}

#[test]
fn rejects_existing_photo_name_without_overwriting_either_file() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");
    std::fs::write(directory.path().join("生日合照.jpg"), b"existing").unwrap();
    let mut saved = false;

    let error = persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("生日合照", "家人"),
        |_| {
            saved = true;
            Ok(())
        },
    )
    .unwrap_err();

    assert!(error.contains("已存在"));
    assert!(!saved);
    assert_eq!(
        std::fs::read(directory.path().join("IMG_001.jpg")).unwrap(),
        b"photo"
    );
    assert_eq!(
        std::fs::read(directory.path().join("生日合照.jpg")).unwrap(),
        b"existing"
    );
    assert_eq!(index.resources[0].id, resource.id);
}

#[test]
fn rejects_invalid_windows_photo_names_before_touching_file() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");

    let error = persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("bad:name", "家人"),
        |_| Ok(()),
    )
    .unwrap_err();

    assert!(error.contains("文件名"));
    assert!(directory.path().join("IMG_001.jpg").exists());
    assert_eq!(index.resources[0].id, resource.id);
}

#[test]
fn restores_original_photo_name_when_index_save_fails() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");

    let error = persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("生日合照", "家人"),
        |_| Err("无法写入 JSON".to_owned()),
    )
    .unwrap_err();

    assert!(error.contains("无法写入 JSON"));
    assert!(directory.path().join("IMG_001.jpg").exists());
    assert!(!directory.path().join("生日合照.jpg").exists());
    assert_eq!(index.resources[0].id, resource.id);
}

#[test]
fn renames_photo_when_only_title_letter_case_changes() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, resource) = indexed_photo(directory.path(), "IMG_001.jpg");

    persist_resource_metadata(
        &mut index,
        &resource.id,
        photo_metadata("img_001", "家人"),
        |_| Ok(()),
    )
    .unwrap();

    assert_eq!(index.resources[0].name, "img_001.jpg");
    assert!(directory.path().join("img_001.jpg").exists());
}

#[test]
fn move_photos_preserves_both_files_and_metadata_in_existing_child_directory() {
    let directory = tempfile::tempdir().unwrap();
    let child = directory.path().join("旅行");
    std::fs::create_dir(&child).unwrap();
    let (mut index, first) = indexed_photo(directory.path(), "one.jpg");
    std::fs::write(directory.path().join("two.jpg"), b"second").unwrap();
    let scan = scan_roots(ResourceKind::Photo, &[directory.path().to_path_buf()]);
    index.resources = scan.resources;
    index.photo_directories = scan.directories;
    index
        .metadata_by_id
        .insert(first.id.clone(), photo_metadata("one", ""));
    let ids: Vec<String> = index
        .resources
        .iter()
        .map(|resource| resource.id.clone())
        .collect();

    move_photos(&mut index, &ids, &child, None, |_| Ok(())).unwrap();

    assert!(!directory.path().join("one.jpg").exists());
    assert!(!directory.path().join("two.jpg").exists());
    assert!(child.join("one.jpg").exists());
    assert!(child.join("two.jpg").exists());
    let moved = index
        .resources
        .iter()
        .find(|resource| resource.name == "one.jpg")
        .unwrap();
    assert_eq!(moved.path, child.join("one.jpg").canonicalize().unwrap());
    assert!(!index.metadata_by_id.contains_key(&first.id));
    assert!(index.metadata_by_id.contains_key(&moved.id));
    match index.metadata_by_id.get(&moved.id).unwrap() {
        ResourceMetadata::Photo(details) => assert_eq!(details.album, "旅行"),
        _ => panic!("应保留照片资料"),
    }
}

#[test]
fn move_photos_creates_a_child_directory_and_updates_directory_index() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, photo) = indexed_photo(directory.path(), "one.jpg");
    index.photo_directories = vec![directory.path().canonicalize().unwrap()];

    move_photos(
        &mut index,
        &[photo.id],
        directory.path(),
        Some("新相册"),
        |_| Ok(()),
    )
    .unwrap();

    let target = directory.path().join("新相册");
    assert!(target.join("one.jpg").exists());
    assert!(
        index
            .photo_directories
            .contains(&target.canonicalize().unwrap())
    );
}

#[test]
fn move_photos_refuses_name_collision_before_moving_any_photo() {
    let directory = tempfile::tempdir().unwrap();
    let child = directory.path().join("旅行");
    std::fs::create_dir(&child).unwrap();
    let (mut index, _) = indexed_photo(directory.path(), "one.jpg");
    std::fs::write(directory.path().join("two.jpg"), b"second").unwrap();
    std::fs::write(child.join("two.jpg"), b"existing").unwrap();
    let scan = scan_roots(ResourceKind::Photo, &[directory.path().to_path_buf()]);
    index.resources = scan.resources;
    index.photo_directories = scan.directories;
    let ids: Vec<String> = index
        .resources
        .iter()
        .filter(|resource| resource.path.parent() == Some(directory.path()))
        .map(|resource| resource.id.clone())
        .collect();

    assert!(move_photos(&mut index, &ids, &child, None, |_| Ok(())).is_err());
    assert!(directory.path().join("one.jpg").exists());
    assert!(directory.path().join("two.jpg").exists());
    assert_eq!(std::fs::read(child.join("two.jpg")).unwrap(), b"existing");
}

#[test]
fn move_photos_rolls_back_files_when_index_save_fails() {
    let directory = tempfile::tempdir().unwrap();
    let (mut index, photo) = indexed_photo(directory.path(), "one.jpg");
    std::fs::write(directory.path().join("two.jpg"), b"second").unwrap();
    index.resources = scan_roots(ResourceKind::Photo, &[directory.path().to_path_buf()]).resources;
    index.photo_directories =
        scan_roots(ResourceKind::Photo, &[directory.path().to_path_buf()]).directories;
    let ids: Vec<String> = index
        .resources
        .iter()
        .map(|resource| resource.id.clone())
        .collect();

    let error = move_photos(&mut index, &ids, directory.path(), Some("旅行"), |_| {
        Err("无法写入 JSON".to_owned())
    })
    .unwrap_err();

    assert!(error.contains("无法写入 JSON"));
    assert!(directory.path().join("one.jpg").exists());
    assert!(directory.path().join("two.jpg").exists());
    assert!(!directory.path().join("旅行").exists());
    assert!(
        index
            .resources
            .iter()
            .any(|resource| resource.id == photo.id)
    );
}

#[test]
fn move_photos_rejects_outside_directory_and_invalid_new_folder_name() {
    let directory = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    let (mut index, photo) = indexed_photo(directory.path(), "one.jpg");
    index.photo_directories = vec![directory.path().canonicalize().unwrap()];

    assert!(
        move_photos(
            &mut index,
            &[photo.id.clone()],
            outside.path(),
            None,
            |_| Ok(())
        )
        .is_err()
    );
    assert!(
        move_photos(
            &mut index,
            &[photo.id],
            directory.path(),
            Some("../escape"),
            |_| Ok(())
        )
        .is_err()
    );
    assert!(directory.path().join("one.jpg").exists());
    assert!(!directory.path().join("escape").exists());
}
