use std::collections::{BTreeMap, HashSet};
use std::fs::{self, File};
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use chrono::Utc;
use serde::{Deserialize, Serialize};

use crate::scanner::{ResourceEntry, ResourceKind, ScanResult, ScanWarning, create_resource_entry};

#[derive(Clone, Debug, Default, Deserialize, Serialize)]
pub struct ResourceRoots {
    pub photos: Vec<PathBuf>,
    pub movies: Vec<PathBuf>,
    pub notes: Vec<PathBuf>,
    pub exams: Vec<PathBuf>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourceIndex {
    pub schema_version: u32,
    pub family_id: i64,
    pub roots: ResourceRoots,
    pub last_scanned_at: Option<String>,
    pub resources: Vec<ResourceEntry>,
    pub warnings: Vec<ScanWarning>,
    #[serde(default)]
    pub photo_directories: Vec<PathBuf>,
    #[serde(default)]
    pub metadata_by_id: BTreeMap<String, ResourceMetadata>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(tag = "kind", content = "details", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ResourceMetadata {
    Photo(PhotoMetadata),
    Movie(MovieMetadata),
    Exam(ExamMetadata),
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PhotoMetadata {
    pub title: String,
    pub album: String,
    pub date: String,
    pub description: String,
    #[serde(default)]
    pub categories: Vec<String>,
    #[serde(default)]
    pub rating: Option<u8>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct MovieMetadata {
    pub title: String,
    pub movie_type: String,
    pub year: Option<u16>,
    pub director: String,
    pub minutes: Option<u16>,
    pub rating: Option<f64>,
    pub status: String,
    pub progress: u8,
    pub description: String,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ExamMetadata {
    pub title: String,
    pub subject: String,
    pub grade: String,
    pub date: String,
    pub full_score: u16,
    pub score: Option<f64>,
    pub status: String,
    pub note: String,
}

impl ResourceMetadata {
    pub fn validate_for(&self, kind: ResourceKind) -> Result<(), String> {
        let (title, fields): (&str, Vec<&str>) = match (self, kind) {
            (Self::Photo(value), ResourceKind::Photo) => {
                if value.rating.is_some_and(|rating| rating > 5)
                    || value
                        .categories
                        .iter()
                        .any(|category| category.trim().is_empty() || category.trim().len() > 50)
                    || value
                        .categories
                        .iter()
                        .enumerate()
                        .any(|(index, category)| {
                            value.categories[..index]
                                .iter()
                                .any(|previous| previous.trim() == category.trim())
                        })
                {
                    return Err("照片分类或评分无效".to_owned());
                }
                (
                    &value.title,
                    vec![&value.album, &value.date, &value.description],
                )
            }
            (Self::Movie(value), ResourceKind::Movie) => {
                if value.progress > 100
                    || value
                        .year
                        .is_some_and(|year| !(1900..=2100).contains(&year))
                    || value.minutes == Some(0)
                    || value.rating.is_some_and(|rating| {
                        !rating.is_finite() || !(0.0..=10.0).contains(&rating)
                    })
                {
                    return Err("影片资料数值无效".to_owned());
                }
                (
                    &value.title,
                    vec![
                        &value.movie_type,
                        &value.director,
                        &value.status,
                        &value.description,
                    ],
                )
            }
            (Self::Exam(value), ResourceKind::Exam) => {
                if value.full_score == 0
                    || value.score.is_some_and(|score| {
                        !score.is_finite() || score < 0.0 || score > f64::from(value.full_score)
                    })
                {
                    return Err("试卷分数无效".to_owned());
                }
                (
                    &value.title,
                    vec![
                        &value.subject,
                        &value.grade,
                        &value.date,
                        &value.status,
                        &value.note,
                    ],
                )
            }
            _ => return Err("资料类型与文件类型不匹配".to_owned()),
        };
        if title.trim().is_empty()
            || title.chars().count() > 200
            || fields.iter().any(|field| field.chars().count() > 2000)
        {
            return Err("资料标题不能为空，字段长度不能超过限制".to_owned());
        }
        Ok(())
    }
}

impl ResourceIndex {
    pub fn empty(family_id: i64) -> Self {
        Self {
            schema_version: 2,
            family_id,
            roots: ResourceRoots::default(),
            last_scanned_at: None,
            resources: Vec::new(),
            warnings: Vec::new(),
            photo_directories: Vec::new(),
            metadata_by_id: BTreeMap::new(),
        }
    }
}

pub fn index_path(family_id: i64) -> Result<PathBuf, String> {
    if family_id <= 0 {
        return Err("家庭 ID 无效".to_owned());
    }
    let config_dir = dirs::config_dir().ok_or_else(|| "无法获取系统 AppData 目录".to_owned())?;
    Ok(config_dir
        .join("FamilyAdmin")
        .join("families")
        .join(family_id.to_string())
        .join("resource-index.json"))
}

pub fn load_index(family_id: i64) -> Result<ResourceIndex, String> {
    let path = index_path(family_id)?;
    if !path.exists() {
        return Ok(ResourceIndex::empty(family_id));
    }
    let json = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    match serde_json::from_str::<ResourceIndex>(&json) {
        Ok(index) if index.family_id == family_id => Ok(index),
        Ok(_) => Err("资源索引与当前家庭不匹配".to_owned()),
        Err(error) => {
            let corrupt_path = path.with_file_name(format!(
                "resource-index.corrupt-{}.json",
                Utc::now().format("%Y%m%d%H%M%S")
            ));
            fs::rename(&path, &corrupt_path).map_err(|rename_error| rename_error.to_string())?;
            let mut empty = ResourceIndex::empty(family_id);
            empty.warnings.push(ScanWarning {
                path: corrupt_path.to_string_lossy().into_owned(),
                reason: format!("原索引损坏，已备份：{error}"),
            });
            Ok(empty)
        }
    }
}

pub fn save_index(index: &ResourceIndex) -> Result<(), String> {
    let path = index_path(index.family_id)?;
    let parent = path.parent().ok_or_else(|| "索引路径无效".to_owned())?;
    fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    let temp_path = parent.join("resource-index.tmp");
    let content = serde_json::to_vec_pretty(index).map_err(|error| error.to_string())?;
    let mut file = File::create(&temp_path).map_err(|error| error.to_string())?;
    file.write_all(&content)
        .map_err(|error| error.to_string())?;
    file.sync_all().map_err(|error| error.to_string())?;
    replace_file(&temp_path, &path).map_err(|error| error.to_string())
}

/// 修改照片标题时同步改名并迁移路径索引；保存 JSON 失败则尝试恢复原文件名。
pub fn persist_resource_metadata(
    index: &mut ResourceIndex,
    resource_id: &str,
    metadata: ResourceMetadata,
    persist: impl FnOnce(&ResourceIndex) -> Result<(), String>,
) -> Result<(), String> {
    let resource = index
        .resources
        .iter()
        .find(|resource| resource.id == resource_id)
        .ok_or_else(|| "文件不在当前家庭索引中，请重新扫描".to_owned())?;
    metadata.validate_for(resource.kind)?;

    let requested_photo_title = match &metadata {
        ResourceMetadata::Photo(photo) => Some(photo.title.as_str()),
        _ => None,
    };
    let current_photo_title = match index.metadata_by_id.get(resource_id) {
        Some(ResourceMetadata::Photo(photo)) => Some(photo.title.as_str()),
        _ if resource.kind == ResourceKind::Photo => {
            resource.path.file_stem().and_then(|stem| stem.to_str())
        }
        _ => None,
    };
    let title_changed =
        requested_photo_title.is_some_and(|requested| Some(requested) != current_photo_title);

    if !title_changed {
        let mut updated = index.clone();
        updated
            .metadata_by_id
            .insert(resource_id.to_owned(), metadata);
        updated.schema_version = 2;
        persist(&updated)?;
        *index = updated;
        return Ok(());
    }

    let source = find_photo_for_deletion(index, resource_id)?;
    let extension = source
        .path
        .extension()
        .and_then(|value| value.to_str())
        .ok_or_else(|| "照片文件没有有效扩展名，请重新扫描".to_owned())?;
    let requested_title = requested_photo_title.expect("照片标题变更仅适用于照片资料");
    validate_photo_file_title(requested_title, extension)?;
    let target = source
        .path
        .with_file_name(format!("{requested_title}.{extension}"));

    if target == source.path {
        let mut updated = index.clone();
        updated
            .metadata_by_id
            .insert(resource_id.to_owned(), metadata);
        updated.schema_version = 2;
        persist(&updated)?;
        *index = updated;
        return Ok(());
    }
    if target.exists() && target.canonicalize().ok().as_ref() != Some(&source.path) {
        return Err("同目录下已存在同名照片，请修改标题".to_owned());
    }

    rename_photo_without_overwrite(&source.path, &target)
        .map_err(|error| format!("照片文件重命名失败：{error}"))?;
    let renamed = match create_resource_entry(ResourceKind::Photo, &target) {
        Ok(value) => value,
        Err(error) => {
            return Err(rollback_photo_rename(
                &target,
                &source.path,
                error.to_string(),
            ));
        }
    };
    if renamed.id != resource_id && index.resources.iter().any(|entry| entry.id == renamed.id) {
        return Err(rollback_photo_rename(
            &target,
            &source.path,
            "新照片路径与已有索引冲突".to_owned(),
        ));
    }

    let mut updated = index.clone();
    *updated
        .resources
        .iter_mut()
        .find(|entry| entry.id == resource_id)
        .expect("原照片仍在索引中") = renamed.clone();
    updated
        .resources
        .sort_by(|left, right| left.path.cmp(&right.path));
    updated.metadata_by_id.remove(resource_id);
    updated.metadata_by_id.insert(renamed.id, metadata);
    updated.schema_version = 2;
    if let Err(error) = persist(&updated) {
        return Err(rollback_photo_rename(&target, &source.path, error));
    }
    *index = updated;
    Ok(())
}

/// 在当前家庭登记的照片目录内移动整批照片；任一步失败即尝试恢复所有已移动原图。
pub fn move_photos(
    index: &mut ResourceIndex,
    resource_ids: &[String],
    destination_parent: &Path,
    new_folder_name: Option<&str>,
    persist: impl FnOnce(&ResourceIndex) -> Result<(), String>,
) -> Result<(), String> {
    if resource_ids.is_empty()
        || resource_ids.iter().collect::<HashSet<_>>().len() != resource_ids.len()
    {
        return Err("请选择不重复的照片".to_owned());
    }
    let parent_type = fs::symlink_metadata(destination_parent)
        .map_err(|_| "目标文件夹不存在，请重新扫描".to_owned())?
        .file_type();
    if !parent_type.is_dir() || parent_type.is_symlink() {
        return Err("目标不是普通文件夹".to_owned());
    }
    let parent = destination_parent
        .canonicalize()
        .map_err(|error| error.to_string())?;
    if !index
        .photo_directories
        .iter()
        .any(|directory| directory == &parent)
    {
        return Err("目标文件夹不在当前家庭照片目录中，请重新扫描".to_owned());
    }
    let roots: Vec<PathBuf> = index
        .roots
        .photos
        .iter()
        .filter_map(|root| root.canonicalize().ok())
        .collect();
    if !roots.iter().any(|root| parent.starts_with(root)) {
        return Err("目标文件夹超出当前家庭照片根目录".to_owned());
    }
    if new_folder_name.is_none() && roots.contains(&parent) {
        return Err("请选择根目录下的子文件夹".to_owned());
    }
    if let Some(name) = new_folder_name {
        validate_photo_folder_name(name)?;
    }
    let photos: Vec<ResourceEntry> = resource_ids
        .iter()
        .map(|id| find_photo_for_deletion(index, id))
        .collect::<Result<_, _>>()?;

    let requested_directory =
        new_folder_name.map_or_else(|| parent.clone(), |name| parent.join(name));
    let mut created_directory = false;
    if !requested_directory.exists() {
        fs::create_dir(&requested_directory)
            .map_err(|error| format!("创建子文件夹失败：{error}"))?;
        created_directory = true;
    }
    let destination_type = fs::symlink_metadata(&requested_directory)
        .map_err(|error| error.to_string())?
        .file_type();
    if !destination_type.is_dir() || destination_type.is_symlink() {
        return Err("目标不是普通文件夹".to_owned());
    }
    let destination = requested_directory
        .canonicalize()
        .map_err(|error| error.to_string())?;
    if !destination.starts_with(&parent) || roots.contains(&destination) {
        return Err("目标必须是当前照片根目录下的子文件夹".to_owned());
    }
    let destination_name = destination
        .file_name()
        .and_then(|name| name.to_str())
        .ok_or_else(|| "无法读取目标子文件夹名称".to_owned())?
        .to_owned();
    let mut pending = Vec::new();
    for photo in photos {
        let target = destination.join(&photo.name);
        if target == photo.path {
            continue;
        }
        if target.exists() {
            if created_directory {
                let _ = fs::remove_dir(&destination);
            }
            return Err(format!("目标文件夹已存在同名照片：{}", photo.name));
        }
        pending.push((photo, target));
    }
    if pending.is_empty() {
        return Ok(());
    }

    let mut moved = Vec::new();
    let mut updated = index.clone();
    for (photo, target) in &pending {
        if let Err(error) = rename_photo_without_overwrite(&photo.path, target) {
            return Err(rollback_photo_moves(
                &moved,
                created_directory.then_some(destination.as_path()),
                format!("照片移动失败：{}：{error}", photo.name),
            ));
        }
        moved.push((photo.path.clone(), target.clone()));
        let new_entry = match create_resource_entry(ResourceKind::Photo, target) {
            Ok(entry) => entry,
            Err(error) => {
                return Err(rollback_photo_moves(
                    &moved,
                    created_directory.then_some(destination.as_path()),
                    format!("照片索引更新失败：{error}"),
                ));
            }
        };
        if new_entry.id != photo.id
            && updated
                .resources
                .iter()
                .any(|entry| entry.id == new_entry.id)
        {
            return Err(rollback_photo_moves(
                &moved,
                created_directory.then_some(destination.as_path()),
                "新照片路径与已有索引冲突".to_owned(),
            ));
        }
        *updated
            .resources
            .iter_mut()
            .find(|entry| entry.id == photo.id)
            .expect("原照片仍在索引中") = new_entry.clone();
        if let Some(mut metadata) = updated.metadata_by_id.remove(&photo.id) {
            if let ResourceMetadata::Photo(details) = &mut metadata {
                details.album = destination_name.clone();
            }
            updated.metadata_by_id.insert(new_entry.id, metadata);
        }
    }
    updated
        .resources
        .sort_by(|left, right| left.path.cmp(&right.path));
    if !updated.photo_directories.contains(&destination) {
        updated.photo_directories.push(destination.clone());
        updated.photo_directories.sort();
    }
    if let Err(error) = persist(&updated) {
        return Err(rollback_photo_moves(
            &moved,
            created_directory.then_some(destination.as_path()),
            format!("索引保存失败：{error}"),
        ));
    }
    *index = updated;
    Ok(())
}

fn validate_photo_folder_name(name: &str) -> Result<(), String> {
    if name.is_empty()
        || name.trim() != name
        || name.ends_with('.')
        || name.encode_utf16().count() > 255
        || name
            .chars()
            .any(|character| character < ' ' || "<>:\"/\\|?*".contains(character))
    {
        return Err("子文件夹名称不能作为 Windows 文件名".to_owned());
    }
    let basename = name
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    if matches!(basename.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || ((basename.starts_with("COM") || basename.starts_with("LPT"))
            && basename.len() == 4
            && basename.as_bytes()[3].is_ascii_digit()
            && basename.as_bytes()[3] != b'0')
    {
        return Err("子文件夹名称是 Windows 保留名称".to_owned());
    }
    Ok(())
}

fn rollback_photo_moves(
    moved: &[(PathBuf, PathBuf)],
    created_directory: Option<&Path>,
    error: String,
) -> String {
    let mut rollback_errors = Vec::new();
    for (source, target) in moved.iter().rev() {
        if let Err(rollback_error) = rename_photo_without_overwrite(target, source) {
            rollback_errors.push(format!("{}：{rollback_error}", source.display()));
        }
    }
    if let Some(directory) = created_directory {
        if rollback_errors.is_empty() {
            let _ = fs::remove_dir(directory);
        }
    }
    if rollback_errors.is_empty() {
        error
    } else {
        format!(
            "{error}；部分照片无法恢复原位，请重新扫描：{}",
            rollback_errors.join("；")
        )
    }
}

fn validate_photo_file_title(title: &str, extension: &str) -> Result<(), String> {
    if title.trim() != title
        || title.ends_with('.')
        || title
            .chars()
            .any(|character| character < ' ' || "<>:\"/\\|?*".contains(character))
        || title.encode_utf16().count() + extension.encode_utf16().count() + 1 > 255
    {
        return Err("照片标题不能作为 Windows 文件名，请修改后重试".to_owned());
    }
    let basename = title
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    let reserved = matches!(basename.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        || ((basename.starts_with("COM") || basename.starts_with("LPT"))
            && basename.len() == 4
            && basename.as_bytes()[3].is_ascii_digit()
            && basename.as_bytes()[3] != b'0');
    if reserved {
        return Err("照片标题是 Windows 保留文件名，请修改后重试".to_owned());
    }
    Ok(())
}

fn rollback_photo_rename(renamed: &Path, original: &Path, error: String) -> String {
    match rename_photo_without_overwrite(renamed, original) {
        Ok(()) => format!("索引保存失败，已恢复原照片文件名：{error}"),
        Err(rollback_error) => format!(
            "照片已改名，但索引保存失败且无法恢复原文件名：{error}；{rollback_error}。请重新扫描"
        ),
    }
}

#[cfg(windows)]
fn rename_photo_without_overwrite(source: &Path, target: &Path) -> Result<(), String> {
    use std::os::windows::ffi::OsStrExt;
    use windows::Win32::Storage::FileSystem::MoveFileW;
    use windows::core::PCWSTR;

    let source_wide: Vec<u16> = source.as_os_str().encode_wide().chain([0]).collect();
    let target_wide: Vec<u16> = target.as_os_str().encode_wide().chain([0]).collect();
    unsafe { MoveFileW(PCWSTR(source_wide.as_ptr()), PCWSTR(target_wide.as_ptr())) }
        .map_err(|error| error.to_string())
}

#[cfg(not(windows))]
fn rename_photo_without_overwrite(source: &Path, target: &Path) -> Result<(), String> {
    if target.exists() {
        return Err("同目录下已存在同名照片".to_owned());
    }
    fs::rename(source, target).map_err(|error| error.to_string())
}

pub fn merge_scan_result(
    index: &mut ResourceIndex,
    kind: ResourceKind,
    roots: Vec<PathBuf>,
    result: ScanResult,
) {
    index.schema_version = 2;
    index.resources.retain(|resource| resource.kind != kind);
    index.resources.extend(result.resources);
    index
        .resources
        .sort_by(|left, right| left.path.cmp(&right.path));
    index.warnings = result.warnings;
    index.last_scanned_at = Some(Utc::now().to_rfc3339());
    match kind {
        ResourceKind::Photo => {
            index.roots.photos = roots;
            index.photo_directories = result.directories;
        }
        ResourceKind::Movie => index.roots.movies = roots,
        ResourceKind::Note => index.roots.notes = roots,
        ResourceKind::Exam => index.roots.exams = roots,
    }
}

pub fn find_photo_for_deletion(
    index: &ResourceIndex,
    resource_id: &str,
) -> Result<ResourceEntry, String> {
    let resource = index
        .resources
        .iter()
        .find(|resource| resource.id == resource_id)
        .ok_or_else(|| "照片不在当前家庭索引中，请重新扫描".to_owned())?;
    if resource.kind != ResourceKind::Photo {
        return Err("只能将照片移入回收站".to_owned());
    }
    let file_type = fs::symlink_metadata(&resource.path)
        .map_err(|_| "照片文件不存在，请重新扫描".to_owned())?
        .file_type();
    if !file_type.is_file() || file_type.is_symlink() {
        return Err("目标不是普通照片文件".to_owned());
    }
    let canonical_path = resource
        .path
        .canonicalize()
        .map_err(|_| "照片文件不存在，请重新扫描".to_owned())?;
    if canonical_path != resource.path {
        return Err("照片路径已变化，请重新扫描".to_owned());
    }
    let inside_root = index.roots.photos.iter().any(|root| {
        root.canonicalize()
            .map(|canonical_root| canonical_path.starts_with(canonical_root))
            .unwrap_or(false)
    });
    if !inside_root {
        return Err("照片已超出当前家庭登记的扫描目录".to_owned());
    }
    Ok(resource.clone())
}

pub fn remove_deleted_photo(index: &mut ResourceIndex, resource_id: &str) {
    index
        .resources
        .retain(|resource| resource.id != resource_id);
    index.metadata_by_id.remove(resource_id);
}

pub fn find_validated_resource(
    index: &ResourceIndex,
    requested_path: &Path,
) -> Result<ResourceEntry, String> {
    let canonical_path = requested_path
        .canonicalize()
        .map_err(|_| "文件不存在，请重新扫描".to_owned())?;
    let resource = index
        .resources
        .iter()
        .find(|resource| resource.path == canonical_path)
        .cloned()
        .ok_or_else(|| "文件不在当前家庭索引中".to_owned())?;
    let roots = roots_for_kind(&index.roots, resource.kind);
    let inside_root = roots.iter().any(|root| {
        root.canonicalize()
            .map(|canonical_root| canonical_path.starts_with(canonical_root))
            .unwrap_or(false)
    });
    if !inside_root {
        return Err("文件已超出登记的扫描目录".to_owned());
    }
    Ok(resource)
}

fn roots_for_kind(roots: &ResourceRoots, kind: ResourceKind) -> &[PathBuf] {
    match kind {
        ResourceKind::Photo => &roots.photos,
        ResourceKind::Movie => &roots.movies,
        ResourceKind::Note => &roots.notes,
        ResourceKind::Exam => &roots.exams,
    }
}

#[cfg(windows)]
fn replace_file(temp_path: &Path, target_path: &Path) -> io::Result<()> {
    if !target_path.exists() {
        return fs::rename(temp_path, target_path);
    }
    use std::os::windows::ffi::OsStrExt;
    use windows::Win32::Storage::FileSystem::{REPLACE_FILE_FLAGS, ReplaceFileW};
    use windows::core::PCWSTR;

    let target: Vec<u16> = target_path
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    let replacement: Vec<u16> = temp_path.as_os_str().encode_wide().chain(Some(0)).collect();
    unsafe {
        ReplaceFileW(
            PCWSTR(target.as_ptr()),
            PCWSTR(replacement.as_ptr()),
            PCWSTR::null(),
            REPLACE_FILE_FLAGS(0),
            None,
            None,
        )
        .map_err(io::Error::other)
    }
}

#[cfg(not(windows))]
fn replace_file(temp_path: &Path, target_path: &Path) -> io::Result<()> {
    fs::rename(temp_path, target_path)
}
