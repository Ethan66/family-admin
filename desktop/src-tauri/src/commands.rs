use std::fs;
use std::path::PathBuf;
use std::process::Command;

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use serde::Serialize;

use crate::index_store::{
    ResourceIndex, ResourceMetadata, find_photo_directory_for_deletion, find_photo_for_deletion,
    find_validated_resource, hydrate_photo_root_metadata, load_index, merge_scan_result,
    persist_resource_metadata, photo_root_json_files, remove_deleted_photo,
    remove_deleted_photo_directory, save_index,
};
use crate::recycle_bin::recycle_file;
use crate::scanner::{ResourceKind, scan_roots};

const MAX_TEXT_PREVIEW_BYTES: u64 = 2 * 1024 * 1024;
const MAX_BINARY_PREVIEW_BYTES: u64 = 50 * 1024 * 1024;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PreviewData {
    mime_type: String,
    base64: String,
}

#[tauri::command]
pub fn load_resource_index(family_id: i64) -> Result<ResourceIndex, String> {
    load_index(family_id)
}

#[tauri::command]
pub fn list_photo_root_json_files(family_id: i64) -> Result<Vec<PathBuf>, String> {
    let index = load_index(family_id)?;
    photo_root_json_files(&index)
}

#[tauri::command]
pub fn scan_resources(
    family_id: i64,
    kind: ResourceKind,
    roots: Vec<PathBuf>,
) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    let result = scan_roots(kind, &roots);
    merge_scan_result(&mut index, kind, roots, result);
    if kind == ResourceKind::Photo {
        hydrate_photo_root_metadata(&mut index)?;
    }
    save_index(&index)?;
    Ok(index)
}

#[tauri::command]
pub fn save_resource_metadata(
    family_id: i64,
    resource_id: String,
    metadata: ResourceMetadata,
) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    persist_resource_metadata(&mut index, &resource_id, metadata, save_index)?;
    Ok(index)
}

/// 仅回收当前家庭索引中的照片；文件成功移入回收站后再移除其索引和资料。
#[tauri::command]
pub fn recycle_photo(family_id: i64, resource_id: String) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    let resource = find_photo_for_deletion(&index, &resource_id)?;
    recycle_file(&resource.path)?;
    remove_deleted_photo(&mut index, &resource_id);
    save_index(&index)
        .map_err(|error| format!("照片已移入回收站，但索引更新失败，请重新扫描：{error}"))?;
    Ok(index)
}

/// 将当前家庭已扫描的子文件夹整体移入系统回收站，再同步清理其索引。
#[tauri::command]
pub fn recycle_photo_directory(family_id: i64, path: PathBuf) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    let directory = find_photo_directory_for_deletion(&index, &path)?;
    recycle_file(&directory)?;
    remove_deleted_photo_directory(&mut index, &directory);
    save_index(&index)
        .map_err(|error| format!("文件夹已移入回收站，但索引更新失败，请重新扫描：{error}"))?;
    Ok(index)
}

#[tauri::command]
pub fn create_photo_subfolder(
    family_id: i64,
    parent_path: PathBuf,
    folder_name: String,
) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    crate::index_store::create_photo_subfolder(&mut index, &parent_path, &folder_name, save_index)?;
    Ok(index)
}

#[tauri::command]
pub fn move_photos(
    family_id: i64,
    resource_ids: Vec<String>,
    destination_parent: PathBuf,
    new_folder_name: Option<String>,
) -> Result<ResourceIndex, String> {
    let mut index = load_index(family_id)?;
    crate::index_store::move_photos(
        &mut index,
        &resource_ids,
        &destination_parent,
        new_folder_name.as_deref(),
        save_index,
    )?;
    Ok(index)
}

#[tauri::command]
pub fn read_text_preview(family_id: i64, path: PathBuf) -> Result<String, String> {
    let index = load_index(family_id)?;
    let resource = find_validated_resource(&index, &path)?;
    if resource.kind != ResourceKind::Note {
        return Err("仅学习笔记支持文本预览".to_owned());
    }
    if resource.size > MAX_TEXT_PREVIEW_BYTES {
        return Err("笔记超过 2 MB，无法在应用内预览".to_owned());
    }
    fs::read_to_string(resource.path).map_err(|error| error.to_string())
}

#[tauri::command]
pub fn read_binary_preview(family_id: i64, path: PathBuf) -> Result<PreviewData, String> {
    let index = load_index(family_id)?;
    let resource = find_validated_resource(&index, &path)?;
    if resource.kind != ResourceKind::Photo && resource.kind != ResourceKind::Exam {
        return Err("当前资源不支持应用内预览".to_owned());
    }
    if resource.size > MAX_BINARY_PREVIEW_BYTES {
        return Err("文件超过 50 MB，无法在应用内预览".to_owned());
    }
    let mime_type =
        mime_for_extension(&resource.extension).ok_or_else(|| "无法识别文件预览格式".to_owned())?;
    let bytes = fs::read(resource.path).map_err(|error| error.to_string())?;
    Ok(PreviewData {
        mime_type: mime_type.to_owned(),
        base64: STANDARD.encode(bytes),
    })
}

#[tauri::command]
pub fn open_with_default_app(family_id: i64, path: PathBuf) -> Result<(), String> {
    let index = load_index(family_id)?;
    let resource = find_validated_resource(&index, &path)?;
    if resource.kind != ResourceKind::Movie {
        return Err("仅影片可调用系统默认播放器".to_owned());
    }
    opener::open(resource.path).map_err(|error| error.to_string())
}

#[tauri::command]
pub fn reveal_in_explorer(family_id: i64, path: PathBuf) -> Result<(), String> {
    let index = load_index(family_id)?;
    let resource = find_validated_resource(&index, &path)?;
    Command::new("explorer")
        .arg(format!("/select,{}", resource.path.to_string_lossy()))
        .spawn()
        .map_err(|error| error.to_string())?;
    Ok(())
}

fn mime_for_extension(extension: &str) -> Option<&'static str> {
    match extension {
        ".jpg" | ".jpeg" => Some("image/jpeg"),
        ".png" => Some("image/png"),
        ".webp" => Some("image/webp"),
        ".gif" => Some("image/gif"),
        ".pdf" => Some("application/pdf"),
        _ => None,
    }
}
