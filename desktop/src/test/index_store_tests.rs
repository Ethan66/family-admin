use std::path::PathBuf;

use crate::index_store::{
    ExamMetadata, PhotoMetadata, ResourceIndex, ResourceMetadata, create_photo_subfolder,
    find_photo_directory_for_deletion, find_photo_for_deletion, hydrate_photo_root_metadata,
    local_index_snapshot, merge_scan_result, move_photos, persist_resource_metadata,
    photo_root_json_files, remove_deleted_photo, remove_deleted_photo_directory, save_index_at,
    write_photo_root_metadata,
};
use crate::scanner::{ResourceEntry, ResourceKind, ScanResult, create_resource_entry, scan_roots};

#[test]
fn creates_only_scanned_photo_subfolders_and_rolls_back_on_save_failure() {
    let root = tempfile::tempdir().unwrap();
    let outside = tempfile::tempdir().unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().canonicalize().unwrap());
    index
        .photo_directories
        .push(root.path().canonicalize().unwrap());

    assert!(create_photo_subfolder(&mut index, outside.path(), "not-ours", |_| Ok(())).is_err());
    assert!(create_photo_subfolder(&mut index, root.path(), "../escape", |_| Ok(())).is_err());
    assert!(
        create_photo_subfolder(&mut index, root.path(), "旅行", |_| Err(
            "保存失败".to_owned()
        ))
        .is_err()
    );
    assert!(!root.path().join("旅行").exists());

    create_photo_subfolder(&mut index, root.path(), "旅行", |_| Ok(())).unwrap();
    let child = root.path().join("旅行").canonicalize().unwrap();
    assert!(index.photo_directories.contains(&child));
    assert!(create_photo_subfolder(&mut index, root.path(), "旅行", |_| Ok(())).is_err());
}

#[test]
fn subfolder_deletion_rejects_roots_and_unindexed_paths_and_prunes_descendants() {
    let root = tempfile::tempdir().unwrap();
    let child_path = root.path().join("trip");
    std::fs::create_dir_all(child_path.join("day1")).unwrap();
    let child = child_path.canonicalize().unwrap();
    let nested = child.join("day1");
    let photo = nested.join("family.jpg");
    std::fs::write(&photo, b"photo").unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.photo_directories = vec![root.path().to_path_buf(), child.clone(), nested.clone()];
    let resource = create_resource_entry(ResourceKind::Photo, &photo).unwrap();
    index.resources.push(resource.clone());
    index.metadata_by_id.insert(
        resource.id.clone(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "family".to_owned(),
            album: "trip".to_owned(),
            date: "".to_owned(),
            description: "".to_owned(),
            categories: vec![],
            rating: None,
        }),
    );

    assert!(find_photo_directory_for_deletion(&index, root.path()).is_err());
    assert!(find_photo_directory_for_deletion(&index, &root.path().join("unknown")).is_err());
    assert_eq!(
        find_photo_directory_for_deletion(&index, &child_path).unwrap(),
        child
    );
    assert_eq!(
        find_photo_directory_for_deletion(&index, &child).unwrap(),
        child
    );
    remove_deleted_photo_directory(&mut index, &child);
    assert!(!index.photo_directories.contains(&child));
    assert!(!index.photo_directories.contains(&nested));
    assert!(index.resources.is_empty());
    assert!(index.metadata_by_id.is_empty());
}

#[test]
fn subfolder_deletion_protects_nested_json_and_other_added_roots() {
    let root = tempfile::tempdir().unwrap();
    let child_path = root.path().join("trip");
    std::fs::create_dir(&child_path).unwrap();
    let child = child_path.canonicalize().unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.photo_directories = vec![root.path().to_path_buf(), child.clone()];
    std::fs::write(child.join("family-admin-photos.json"), b"{}").unwrap();
    assert!(
        find_photo_directory_for_deletion(&index, &child)
            .unwrap_err()
            .contains("JSON")
    );
    std::fs::remove_file(child.join("family-admin-photos.json")).unwrap();
    index.roots.photos.push(child.clone());
    assert!(find_photo_directory_for_deletion(&index, &child).is_err());
}

#[test]
fn lists_only_existing_root_json_files() {
    let present = tempfile::tempdir().unwrap();
    let absent = tempfile::tempdir().unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos = vec![present.path().to_path_buf(), absent.path().to_path_buf()];
    std::fs::write(present.path().join("photos-setting.json"), b"{}").unwrap();
    assert_eq!(
        photo_root_json_files(&index).unwrap(),
        vec![present.path().to_path_buf()]
    );
}

#[test]
fn migrates_legacy_photo_json_name_without_losing_metadata() {
    let root = tempfile::tempdir().unwrap();
    let photo = root.path().join("family.jpg");
    std::fs::write(&photo, b"photo").unwrap();
    let entry = create_resource_entry(ResourceKind::Photo, &photo).unwrap();
    std::fs::write(
        root.path().join("family-admin-photos.json"),
        serde_json::to_vec(&serde_json::json!({
            "schemaVersion": 1, "familyId": 12,
            "photos": {"family.jpg": {"title": "旧照片标题", "album": "", "date": "", "description": ""}}
        })).unwrap(),
    ).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.resources.push(entry.clone());

    hydrate_photo_root_metadata(&mut index).unwrap();
    assert!(root.path().join("photos-setting.json").is_file());
    assert!(!root.path().join("family-admin-photos.json").exists());
    let Some(ResourceMetadata::Photo(details)) = index.metadata_by_id.get(&entry.id) else {
        panic!("旧照片资料没有恢复")
    };
    assert_eq!(details.title, "旧照片标题");
}

#[test]
fn refuses_to_overwrite_when_old_and_new_photo_json_both_exist() {
    let root = tempfile::tempdir().unwrap();
    let old = root.path().join("family-admin-photos.json");
    let new = root.path().join("photos-setting.json");
    let content =
        serde_json::to_vec(&serde_json::json!({"schemaVersion": 1, "familyId": 12, "photos": {}}))
            .unwrap();
    std::fs::write(&old, &content).unwrap();
    std::fs::write(&new, &content).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    assert!(
        hydrate_photo_root_metadata(&mut index)
            .unwrap_err()
            .contains("同时存在")
    );
    assert!(old.is_file());
    assert!(new.is_file());
}

#[test]
fn failed_index_save_preserves_legacy_metadata_during_filename_migration() {
    let root = tempfile::tempdir().unwrap();
    let legacy = root.path().join("family-admin-photos.json");
    let old_content = serde_json::to_vec(&serde_json::json!({
        "schemaVersion": 1, "familyId": 12, "photos": {}
    }))
    .unwrap();
    std::fs::write(&legacy, &old_content).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    let impossible_target = root.path().join("index-is-a-directory");
    std::fs::create_dir(&impossible_target).unwrap();

    assert!(save_index_at(&index, &impossible_target).is_err());
    let new = root.path().join("photos-setting.json");
    assert!(legacy.is_file() || new.is_file());
    let remaining = if legacy.is_file() { legacy } else { new };
    assert_eq!(std::fs::read(remaining).unwrap(), old_content);
}

#[test]
fn legacy_photo_index_can_delete_a_folder_derived_from_photo_paths() {
    let root = tempfile::tempdir().unwrap();
    let child = root.path().join("trip");
    std::fs::create_dir(&child).unwrap();
    let photo = child.join("family.jpg");
    std::fs::write(&photo, b"photo").unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index
        .resources
        .push(create_resource_entry(ResourceKind::Photo, &photo).unwrap());
    assert_eq!(
        find_photo_directory_for_deletion(&index, &child).unwrap(),
        child.canonicalize().unwrap()
    );
}

#[test]
fn subfolder_deletion_rejects_other_registered_resource_roots() {
    let root = tempfile::tempdir().unwrap();
    let child = root.path().join("trip");
    std::fs::create_dir(&child).unwrap();
    let canonical_child = child.canonicalize().unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.roots.movies.push(child.clone());
    index.photo_directories.push(canonical_child);
    assert!(
        find_photo_directory_for_deletion(&index, &child)
            .unwrap_err()
            .contains("其他资源")
    );
}

#[test]
fn one_root_json_restores_subfolder_photo_metadata_after_drive_path_changes() {
    let source = tempfile::tempdir().unwrap();
    let nested = source.path().join("trip");
    std::fs::create_dir(&nested).unwrap();
    let source_photo = nested.join("family.jpg");
    std::fs::write(&source_photo, b"photo").unwrap();
    let source_entry = create_resource_entry(ResourceKind::Photo, &source_photo).unwrap();
    let mut source_index = ResourceIndex::empty(12);
    source_index.roots.photos.push(source.path().to_path_buf());
    source_index.resources.push(source_entry.clone());
    source_index.metadata_by_id.insert(
        source_entry.id.clone(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "全家福".to_owned(),
            album: "trip".to_owned(),
            date: "".to_owned(),
            description: "跨电脑保留".to_owned(),
            categories: vec!["旅行".to_owned()],
            rating: Some(5),
        }),
    );
    write_photo_root_metadata(&source_index).unwrap();
    assert!(source.path().join("photos-setting.json").exists());
    assert!(!nested.join("photos-setting.json").exists());

    let target = tempfile::tempdir().unwrap();
    let target_nested = target.path().join("trip");
    std::fs::create_dir(&target_nested).unwrap();
    let target_photo = target_nested.join("family.jpg");
    std::fs::copy(&source_photo, &target_photo).unwrap();
    std::fs::copy(
        source.path().join("photos-setting.json"),
        target.path().join("photos-setting.json"),
    )
    .unwrap();
    let target_entry = create_resource_entry(ResourceKind::Photo, &target_photo).unwrap();
    assert_ne!(source_entry.id, target_entry.id);
    let mut target_index = ResourceIndex::empty(12);
    target_index.roots.photos.push(target.path().to_path_buf());
    target_index.resources.push(target_entry.clone());
    hydrate_photo_root_metadata(&mut target_index).unwrap();
    let Some(ResourceMetadata::Photo(details)) = target_index.metadata_by_id.get(&target_entry.id)
    else {
        panic!("照片资料未恢复")
    };
    assert_eq!(details.title, "全家福");
    assert_eq!(details.categories, ["旅行"]);
    assert_eq!(details.rating, Some(5));
}

#[test]
fn a_photo_root_json_cannot_be_claimed_by_another_family() {
    let root = tempfile::tempdir().unwrap();
    let mut first = ResourceIndex::empty(12);
    first.roots.photos.push(root.path().to_path_buf());
    write_photo_root_metadata(&first).unwrap();

    let mut second = ResourceIndex::empty(13);
    second.roots.photos.push(root.path().to_path_buf());
    assert!(
        hydrate_photo_root_metadata(&mut second)
            .unwrap_err()
            .contains("其他家庭")
    );
    assert!(
        write_photo_root_metadata(&second)
            .unwrap_err()
            .contains("其他家庭")
    );
}

#[test]
fn local_index_keeps_other_resource_metadata_but_not_active_photo_details() {
    let mut index = ResourceIndex::empty(12);
    index.resources = vec![
        entry("photo-1", ResourceKind::Photo),
        entry("movie-1", ResourceKind::Movie),
    ];
    index.metadata_by_id.insert(
        "photo-1".to_owned(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "照片".to_owned(),
            album: "".to_owned(),
            date: "".to_owned(),
            description: "".to_owned(),
            categories: vec![],
            rating: Some(4),
        }),
    );
    index.metadata_by_id.insert("movie-1".to_owned(), serde_json::from_value(serde_json::json!({
        "kind": "MOVIE", "details": {"title": "影片", "movieType": "电影", "year": null,
        "director": "", "minutes": null, "rating": null, "status": "想看", "progress": 0, "description": ""}
    })).unwrap());

    let snapshot = local_index_snapshot(&index);
    assert!(!snapshot.metadata_by_id.contains_key("photo-1"));
    assert!(snapshot.metadata_by_id.contains_key("movie-1"));
    assert!(index.metadata_by_id.contains_key("photo-1"));
}

#[test]
fn failed_local_index_write_restores_the_root_json() {
    let root = tempfile::tempdir().unwrap();
    let photo_path = root.path().join("family.jpg");
    std::fs::write(&photo_path, b"photo").unwrap();
    let photo = create_resource_entry(ResourceKind::Photo, &photo_path).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.resources.push(photo.clone());
    let details = PhotoMetadata {
        title: "旧标题".to_owned(),
        album: "".to_owned(),
        date: "".to_owned(),
        description: "".to_owned(),
        categories: vec![],
        rating: None,
    };
    index
        .metadata_by_id
        .insert(photo.id.clone(), ResourceMetadata::Photo(details));
    write_photo_root_metadata(&index).unwrap();
    let json_path = root.path().join("photos-setting.json");
    let before = std::fs::read(&json_path).unwrap();

    let ResourceMetadata::Photo(details) = index.metadata_by_id.get_mut(&photo.id).unwrap() else {
        unreachable!()
    };
    details.title = "新标题".to_owned();
    let impossible_target = root.path().join("central-is-a-directory");
    std::fs::create_dir(&impossible_target).unwrap();
    assert!(save_index_at(&index, &impossible_target).is_err());
    assert_eq!(std::fs::read(&json_path).unwrap(), before);
}

#[test]
fn an_offline_photo_root_does_not_block_local_index_access() {
    let directory = tempfile::tempdir().unwrap();
    let missing_root = directory.path().join("unplugged-drive");
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(missing_root);
    hydrate_photo_root_metadata(&mut index).unwrap();
    save_index_at(&index, &directory.path().join("resource-index.json")).unwrap();
    assert!(directory.path().join("resource-index.json").exists());
}

#[test]
fn an_offline_root_keeps_its_photo_metadata_in_the_local_cache() {
    let directory = tempfile::tempdir().unwrap();
    let mut index = ResourceIndex::empty(12);
    index
        .roots
        .photos
        .push(directory.path().join("unplugged-drive"));
    index.resources.push(entry("photo-1", ResourceKind::Photo));
    index.metadata_by_id.insert(
        "photo-1".to_owned(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "保留".to_owned(),
            album: "".to_owned(),
            date: "".to_owned(),
            description: "".to_owned(),
            categories: vec![],
            rating: None,
        }),
    );
    let local_path = directory.path().join("resource-index.json");
    save_index_at(&index, &local_path).unwrap();
    let saved: ResourceIndex = serde_json::from_slice(&std::fs::read(local_path).unwrap()).unwrap();
    assert!(saved.metadata_by_id.contains_key("photo-1"));
}

#[test]
fn old_local_indexes_cannot_assign_one_photo_root_to_two_families() {
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().join("photos");
    std::fs::create_dir(&root).unwrap();
    let families = directory.path().join("families");
    let other_dir = families.join("13");
    std::fs::create_dir_all(&other_dir).unwrap();
    let mut other = ResourceIndex::empty(13);
    other.roots.photos.push(root.clone());
    std::fs::write(
        other_dir.join("resource-index.json"),
        serde_json::to_vec(&other).unwrap(),
    )
    .unwrap();

    let mut current = ResourceIndex::empty(12);
    current.roots.photos.push(root.clone());
    let own_path = families.join("12").join("resource-index.json");
    assert!(
        save_index_at(&current, &own_path)
            .unwrap_err()
            .contains("其他家庭")
    );
    assert!(!root.join("photos-setting.json").exists());
}

#[test]
fn overlapping_parent_and_child_roots_cannot_be_assigned_to_different_families() {
    let directory = tempfile::tempdir().unwrap();
    let root = directory.path().join("photos");
    let child = root.join("trip");
    std::fs::create_dir_all(&child).unwrap();
    let families = directory.path().join("families");
    let other_dir = families.join("13");
    std::fs::create_dir_all(&other_dir).unwrap();
    let mut other = ResourceIndex::empty(13);
    other.roots.photos.push(root.clone());
    std::fs::write(
        other_dir.join("resource-index.json"),
        serde_json::to_vec(&other).unwrap(),
    )
    .unwrap();
    let mut current = ResourceIndex::empty(12);
    current.roots.photos.push(child);
    let own_path = families.join("12").join("resource-index.json");
    assert!(
        save_index_at(&current, &own_path)
            .unwrap_err()
            .contains("其他家庭")
    );
}

#[test]
fn a_foreign_parent_root_json_blocks_claiming_its_child_folder() {
    let directory = tempfile::tempdir().unwrap();
    let child = directory.path().join("trip");
    std::fs::create_dir(&child).unwrap();
    let mut parent = ResourceIndex::empty(13);
    parent.roots.photos.push(directory.path().to_path_buf());
    write_photo_root_metadata(&parent).unwrap();

    let mut other = ResourceIndex::empty(12);
    other.roots.photos.push(child.clone());
    assert!(
        write_photo_root_metadata(&other)
            .unwrap_err()
            .contains("其他家庭")
    );
    assert!(!child.join("photos-setting.json").exists());
}

#[test]
fn a_foreign_child_root_json_blocks_claiming_its_parent_folder() {
    let directory = tempfile::tempdir().unwrap();
    let child = directory.path().join("trip");
    std::fs::create_dir(&child).unwrap();
    let mut nested = ResourceIndex::empty(13);
    nested.roots.photos.push(child.clone());
    write_photo_root_metadata(&nested).unwrap();

    let mut other = ResourceIndex::empty(12);
    other.roots.photos.push(directory.path().to_path_buf());
    other.photo_directories.push(child);
    assert!(
        write_photo_root_metadata(&other)
            .unwrap_err()
            .contains("其他家庭")
    );
    assert!(!directory.path().join("photos-setting.json").exists());
}

#[test]
fn a_rescan_does_not_erase_metadata_for_a_temporarily_missing_photo() {
    let root = tempfile::tempdir().unwrap();
    let photo_path = root.path().join("family.jpg");
    std::fs::write(&photo_path, b"photo").unwrap();
    let photo = create_resource_entry(ResourceKind::Photo, &photo_path).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.resources.push(photo.clone());
    index.metadata_by_id.insert(
        photo.id,
        ResourceMetadata::Photo(PhotoMetadata {
            title: "保留资料".to_owned(),
            album: "".to_owned(),
            date: "".to_owned(),
            description: "".to_owned(),
            categories: vec![],
            rating: None,
        }),
    );
    write_photo_root_metadata(&index).unwrap();
    index.resources.clear();
    write_photo_root_metadata(&index).unwrap();
    let saved: serde_json::Value =
        serde_json::from_slice(&std::fs::read(root.path().join("photos-setting.json")).unwrap())
            .unwrap();
    assert_eq!(saved["photos"]["family.jpg"]["title"], "保留资料");
}

#[test]
fn renaming_a_photo_removes_its_old_relative_path_from_root_json() {
    let root = tempfile::tempdir().unwrap();
    let photo_path = root.path().join("old.jpg");
    std::fs::write(&photo_path, b"photo").unwrap();
    let photo = create_resource_entry(ResourceKind::Photo, &photo_path).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos.push(root.path().to_path_buf());
    index.resources.push(photo.clone());
    index.metadata_by_id.insert(
        photo.id.clone(),
        ResourceMetadata::Photo(PhotoMetadata {
            title: "old".to_owned(),
            album: "".to_owned(),
            date: "".to_owned(),
            description: "".to_owned(),
            categories: vec![],
            rating: None,
        }),
    );
    let local_path = root.path().join("resource-index.json");
    save_index_at(&index, &local_path).unwrap();
    let metadata = ResourceMetadata::Photo(PhotoMetadata {
        title: "new".to_owned(),
        album: "".to_owned(),
        date: "".to_owned(),
        description: "".to_owned(),
        categories: vec![],
        rating: Some(5),
    });
    persist_resource_metadata(&mut index, &photo.id, metadata, |updated| {
        save_index_at(updated, &local_path)
    })
    .unwrap();
    let saved: serde_json::Value =
        serde_json::from_slice(&std::fs::read(root.path().join("photos-setting.json")).unwrap())
            .unwrap();
    assert_eq!(saved["photos"]["new.jpg"]["rating"], 5);
    assert!(saved["photos"].get("old.jpg").is_none());
}

fn entry(id: &str, kind: ResourceKind) -> ResourceEntry {
    ResourceEntry {
        id: id.to_owned(),
        kind,
        path: PathBuf::from(format!("C:\\resources\\{id}")),
        name: id.to_owned(),
        extension: ".jpg".to_owned(),
        size: 10,
        modified_at: "2026-09-24T10:00:00Z".to_owned(),
        captured_at: None,
        image_width: None,
        image_height: None,
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
fn move_photos_to_an_added_root_clears_album_and_transfers_root_json_metadata() {
    let source = tempfile::tempdir().unwrap();
    let target = tempfile::tempdir().unwrap();
    let state = tempfile::tempdir().unwrap();
    let source_child = source.path().join("旅行");
    std::fs::create_dir(&source_child).unwrap();
    let source_photo = source_child.join("family.jpg");
    std::fs::write(&source_photo, b"photo").unwrap();
    let photo = create_resource_entry(ResourceKind::Photo, &source_photo).unwrap();
    let mut index = ResourceIndex::empty(12);
    index.roots.photos = vec![
        source.path().canonicalize().unwrap(),
        target.path().canonicalize().unwrap(),
    ];
    index.photo_directories = vec![
        index.roots.photos[0].clone(),
        source_child.canonicalize().unwrap(),
        index.roots.photos[1].clone(),
    ];
    index.resources.push(photo.clone());
    index
        .metadata_by_id
        .insert(photo.id.clone(), photo_metadata("family", "旅行"));
    let index_path = state
        .path()
        .join("families")
        .join("12")
        .join("resource-index.json");
    std::fs::create_dir_all(index_path.parent().unwrap()).unwrap();
    save_index_at(&index, &index_path).unwrap();

    move_photos(
        &mut index,
        &[photo.id.clone()],
        target.path(),
        None,
        |updated| save_index_at(updated, &index_path),
    )
    .unwrap();

    assert!(!source_photo.exists());
    assert!(target.path().join("family.jpg").exists());
    let moved = &index.resources[0];
    match index.metadata_by_id.get(&moved.id).unwrap() {
        ResourceMetadata::Photo(details) => assert!(details.album.is_empty()),
        _ => panic!("应保留照片资料"),
    }
    let source_json: serde_json::Value =
        serde_json::from_slice(&std::fs::read(source.path().join("photos-setting.json")).unwrap())
            .unwrap();
    let target_json: serde_json::Value =
        serde_json::from_slice(&std::fs::read(target.path().join("photos-setting.json")).unwrap())
            .unwrap();
    assert!(source_json["photos"].as_object().unwrap().is_empty());
    assert_eq!(target_json["photos"]["family.jpg"]["album"], "");
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
