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

const PHOTO_METADATA_FILE: &str = "photos-setting.json";
const LEGACY_PHOTO_METADATA_FILE: &str = "family-admin-photos.json";

fn existing_photo_metadata_path(root: &Path) -> Result<Option<PathBuf>, String> {
    let current = root.join(PHOTO_METADATA_FILE);
    let legacy = root.join(LEGACY_PHOTO_METADATA_FILE);
    let current_exists = fs::symlink_metadata(&current).is_ok();
    let legacy_exists = fs::symlink_metadata(&legacy).is_ok();
    if current_exists && legacy_exists {
        return Err(format!(
            "照片目录同时存在新旧配置文件，请手动核对后只保留一份：{}",
            root.display()
        ));
    }
    if current_exists {
        return Ok(Some(current));
    }
    if legacy_exists {
        return Ok(Some(legacy));
    }
    Ok(None)
}

/// 旧文件先通过家庭归属及内容校验，再无覆盖地改名；仅显式登记的根目录执行迁移。
fn migrate_legacy_photo_metadata(root: &Path, family_id: i64) -> Result<(), String> {
    let Some(path) = existing_photo_metadata_path(root)? else {
        return Ok(());
    };
    read_photo_root_metadata(root, family_id)?;
    if path
        .file_name()
        .is_some_and(|name| name == LEGACY_PHOTO_METADATA_FILE)
    {
        rename_photo_without_overwrite(&path, &root.join(PHOTO_METADATA_FILE))
            .map_err(|error| format!("照片配置文件改名失败 {}：{error}", root.display()))?;
    }
    Ok(())
}

/// 返回磁盘上确实存在的根目录 JSON；保持原登记路径供目录树精确匹配。
pub fn photo_root_json_files(index: &ResourceIndex) -> Result<Vec<PathBuf>, String> {
    let mut existing = Vec::new();
    for root in &index.roots.photos {
        let path = root.join(PHOTO_METADATA_FILE);
        match fs::symlink_metadata(&path) {
            Ok(metadata) if metadata.file_type().is_file() => existing.push(root.clone()),
            Ok(_) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => return Err(format!("无法检查照片配置文件 {}：{error}", path.display())),
        }
    }
    Ok(existing)
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct PhotoRootMetadata {
    schema_version: u32,
    family_id: i64,
    photos: BTreeMap<String, PhotoMetadata>,
}

fn read_photo_root_metadata(
    root: &Path,
    family_id: i64,
) -> Result<Option<PhotoRootMetadata>, String> {
    let Some(path) = existing_photo_metadata_path(root)? else {
        return Ok(None);
    };
    if fs::symlink_metadata(&path)
        .map_err(|error| error.to_string())?
        .file_type()
        .is_symlink()
    {
        return Err(format!("照片目录配置不能是符号链接：{}", path.display()));
    }
    let content = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    let data: PhotoRootMetadata = serde_json::from_str(&content).map_err(|error| {
        format!(
            "照片目录配置无效，请先备份后修复 {}：{error}",
            path.display()
        )
    })?;
    if data.family_id != family_id {
        return Err(format!(
            "照片目录已属于其他家庭，不能重复添加：{}",
            root.display()
        ));
    }
    if data.schema_version != 1 {
        return Err(format!("照片目录配置版本不受支持：{}", path.display()));
    }
    Ok(Some(data))
}

fn photo_relative_path(root: &Path, path: &Path) -> Option<String> {
    let relative = path.strip_prefix(root).ok()?;
    if relative.as_os_str().is_empty() {
        return None;
    }
    Some(relative.to_string_lossy().replace('\\', "/"))
}

fn photo_owner_root<'a>(roots: &'a [PathBuf], path: &Path) -> Option<&'a PathBuf> {
    roots
        .iter()
        .filter(|root| path.starts_with(root))
        .max_by_key(|root| root.components().count())
}

fn canonical_photo_roots(index: &ResourceIndex) -> Result<Vec<PathBuf>, String> {
    index
        .roots
        .photos
        .iter()
        .filter_map(|root| match root.canonicalize() {
            Ok(path) => Some(Ok(path)),
            Err(error) if error.kind() == io::ErrorKind::NotFound => None,
            Err(error) => Some(Err(format!("照片根目录不可用 {}：{error}", root.display()))),
        })
        .collect()
}

fn ensure_no_foreign_photo_root_json(
    index: &ResourceIndex,
    roots: &[PathBuf],
) -> Result<(), String> {
    for root in roots {
        for ancestor in root.ancestors().skip(1) {
            read_photo_root_metadata(ancestor, index.family_id)?;
        }
    }
    for directory in &index.photo_directories {
        let Ok(canonical) = directory.canonicalize() else {
            continue;
        };
        if roots.iter().any(|root| canonical.starts_with(root)) {
            read_photo_root_metadata(&canonical, index.family_id)?;
        }
    }
    Ok(())
}

/// 从每个手动添加的根目录读取一份资料；相对路径可在换盘符后重新匹配照片。
pub fn hydrate_photo_root_metadata(index: &mut ResourceIndex) -> Result<(), String> {
    let roots = canonical_photo_roots(index)?;
    ensure_no_foreign_photo_root_json(index, &roots)?;
    for root in &roots {
        migrate_legacy_photo_metadata(root, index.family_id)?;
    }
    let stores = roots
        .iter()
        .map(|root| read_photo_root_metadata(root, index.family_id))
        .collect::<Result<Vec<_>, _>>()?;
    for resource in &index.resources {
        if resource.kind != ResourceKind::Photo {
            continue;
        }
        let Some(root) = photo_owner_root(&roots, &resource.path) else {
            continue;
        };
        let Some(key) = photo_relative_path(root, &resource.path) else {
            continue;
        };
        let Some(store) = &stores[roots
            .iter()
            .position(|item| item == root)
            .expect("所属根目录存在")]
        else {
            continue;
        };
        if let Some(details) = store.photos.get(&key) {
            index.metadata_by_id.insert(
                resource.id.clone(),
                ResourceMetadata::Photo(details.clone()),
            );
        }
    }
    Ok(())
}

/// 只在显式添加的根目录写 JSON；子文件夹共用这份文件。
pub fn write_photo_root_metadata(index: &ResourceIndex) -> Result<(), String> {
    write_photo_root_metadata_with_removed(index, &[])
}

fn write_photo_root_metadata_with_removed(
    index: &ResourceIndex,
    removed: &[(PathBuf, String)],
) -> Result<(), String> {
    let roots = canonical_photo_roots(index)?;
    ensure_no_foreign_photo_root_json(index, &roots)?;
    for root in &roots {
        migrate_legacy_photo_metadata(root, index.family_id)?;
    }
    let mut stores = roots
        .iter()
        .map(|root| {
            read_photo_root_metadata(root, index.family_id).map(|existing| {
                existing.unwrap_or(PhotoRootMetadata {
                    schema_version: 1,
                    family_id: index.family_id,
                    photos: BTreeMap::new(),
                })
            })
        })
        .collect::<Result<Vec<_>, _>>()?;
    for (root, key) in removed {
        if let Some(position) = roots.iter().position(|item| item == root) {
            stores[position].photos.remove(key);
        }
    }
    for resource in &index.resources {
        if resource.kind != ResourceKind::Photo {
            continue;
        }
        let Some(ResourceMetadata::Photo(details)) = index.metadata_by_id.get(&resource.id) else {
            continue;
        };
        let Some(root) = photo_owner_root(&roots, &resource.path) else {
            continue;
        };
        let Some(key) = photo_relative_path(root, &resource.path) else {
            continue;
        };
        let position = roots
            .iter()
            .position(|item| item == root)
            .expect("所属根目录存在");
        stores[position].photos.insert(key, details.clone());
    }
    let contents = stores
        .iter()
        .map(|store| serde_json::to_vec_pretty(store).map_err(|error| error.to_string()))
        .collect::<Result<Vec<_>, _>>()?;
    let snapshots = photo_root_snapshots(&roots)?;
    for (root, content) in roots.iter().zip(contents) {
        let path = root.join(PHOTO_METADATA_FILE);
        if let Err(error) = write_bytes_atomically(&path, &content) {
            restore_photo_root_snapshots(&snapshots)?;
            return Err(error);
        }
    }
    Ok(())
}

fn photo_root_snapshots(roots: &[PathBuf]) -> Result<Vec<(PathBuf, Option<Vec<u8>>)>, String> {
    roots
        .iter()
        .map(|root| {
            let path = root.join(PHOTO_METADATA_FILE);
            let old = if path.exists() {
                Some(fs::read(&path).map_err(|error| error.to_string())?)
            } else {
                None
            };
            Ok((path, old))
        })
        .collect()
}

fn restore_photo_root_snapshots(snapshots: &[(PathBuf, Option<Vec<u8>>)]) -> Result<(), String> {
    for (path, old) in snapshots.iter().rev() {
        if let Some(content) = old {
            write_bytes_atomically(path, content)?;
        } else if path.exists() {
            fs::remove_file(path).map_err(|error| error.to_string())?;
        }
    }
    Ok(())
}

fn write_bytes_atomically(path: &Path, content: &[u8]) -> Result<(), String> {
    let parent = path.parent().ok_or_else(|| "索引路径无效".to_owned())?;
    fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    let temp_path = path.with_extension("tmp");
    let mut file = File::create(&temp_path).map_err(|error| error.to_string())?;
    file.write_all(content).map_err(|error| error.to_string())?;
    file.sync_all().map_err(|error| error.to_string())?;
    drop(file);
    if let Err(error) = replace_file(&temp_path, path) {
        let _ = fs::remove_file(&temp_path);
        return Err(error.to_string());
    }
    Ok(())
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

/// 本机索引保留目录、扫描缓存和非照片资料；已索引照片的资料以根目录 JSON 为准。
pub fn local_index_snapshot(index: &ResourceIndex) -> ResourceIndex {
    let mut local = index.clone();
    for resource in &index.resources {
        if resource.kind == ResourceKind::Photo {
            local.metadata_by_id.remove(&resource.id);
        }
    }
    local
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
        Ok(mut index) if index.family_id == family_id => {
            hydrate_photo_root_metadata(&mut index)?;
            Ok(index)
        }
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
    save_index_at(index, &path)
}

fn ensure_photo_roots_not_claimed_elsewhere(
    index: &ResourceIndex,
    index_path: &Path,
) -> Result<(), String> {
    let Some(families_dir) = index_path.parent().and_then(Path::parent) else {
        return Ok(());
    };
    if !families_dir.exists() {
        return Ok(());
    }
    let requested = canonical_photo_roots(index)?;
    for entry in fs::read_dir(families_dir).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry
            .file_type()
            .map_err(|error| error.to_string())?
            .is_dir()
        {
            continue;
        }
        let Some(other_family_id) = entry
            .file_name()
            .to_str()
            .and_then(|name| name.parse::<i64>().ok())
        else {
            continue;
        };
        if other_family_id == index.family_id {
            continue;
        }
        let other_path = entry.path().join("resource-index.json");
        if !other_path.exists() {
            continue;
        }
        let content = fs::read(&other_path).map_err(|error| error.to_string())?;
        let other: ResourceIndex = serde_json::from_slice(&content)
            .map_err(|error| format!("无法核对其他家庭的照片目录：{error}"))?;
        for root in &other.roots.photos {
            if let Ok(canonical) = root.canonicalize() {
                if requested
                    .iter()
                    .any(|root| root.starts_with(&canonical) || canonical.starts_with(root))
                {
                    return Err(format!(
                        "照片目录已属于其他家庭，不能重复添加：{}",
                        canonical.display()
                    ));
                }
            }
        }
    }
    Ok(())
}

pub(crate) fn save_index_at(index: &ResourceIndex, path: &Path) -> Result<(), String> {
    ensure_photo_roots_not_claimed_elsewhere(index, path)?;
    let roots = canonical_photo_roots(index)?;
    for root in &roots {
        migrate_legacy_photo_metadata(root, index.family_id)?;
    }
    let snapshots = photo_root_snapshots(&roots)?;
    let previous = if path.is_file() {
        let content = fs::read(path).map_err(|error| error.to_string())?;
        serde_json::from_slice::<ResourceIndex>(&content).ok()
    } else {
        None
    };
    let mut removed = Vec::new();
    if let Some(previous) = previous {
        for resource in &previous.resources {
            if resource.kind != ResourceKind::Photo
                || index.metadata_by_id.contains_key(&resource.id)
            {
                continue;
            }
            let Some(root) = photo_owner_root(&roots, &resource.path) else {
                continue;
            };
            let Some(key) = photo_relative_path(root, &resource.path) else {
                continue;
            };
            removed.push((root.clone(), key));
        }
    }
    let local = if roots.len() == index.roots.photos.len() {
        local_index_snapshot(index)
    } else {
        index.clone()
    };
    let content = serde_json::to_vec_pretty(&local).map_err(|error| error.to_string())?;
    write_photo_root_metadata_with_removed(index, &removed)?;
    if let Err(error) = write_bytes_atomically(path, &content) {
        restore_photo_root_snapshots(&snapshots)?;
        return Err(error);
    }
    Ok(())
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

/// 只在当前家庭已扫描的照片目录内创建子文件夹；索引保存失败则移除新建的空目录。
pub fn create_photo_subfolder(
    index: &mut ResourceIndex,
    parent_path: &Path,
    folder_name: &str,
    persist: impl FnOnce(&ResourceIndex) -> Result<(), String>,
) -> Result<(), String> {
    validate_photo_folder_name(folder_name)?;
    let parent_type = fs::symlink_metadata(parent_path)
        .map_err(|_| "父文件夹不存在，请重新扫描".to_owned())?
        .file_type();
    if !parent_type.is_dir() || parent_type.is_symlink() {
        return Err("父目录不是普通文件夹".to_owned());
    }
    let parent = parent_path
        .canonicalize()
        .map_err(|error| error.to_string())?;
    if !index.photo_directories.contains(&parent)
        || !canonical_photo_roots(index)?
            .iter()
            .any(|root| parent.starts_with(root))
    {
        return Err("父文件夹不在当前家庭照片目录中，请重新扫描".to_owned());
    }
    let target = parent.join(folder_name);
    fs::create_dir(&target).map_err(|error| format!("创建子文件夹失败：{error}"))?;
    let created = match target.canonicalize() {
        Ok(path) => path,
        Err(error) => {
            let _ = fs::remove_dir(&target);
            return Err(format!("读取新建子文件夹失败：{error}"));
        }
    };
    let mut updated = index.clone();
    updated.photo_directories.push(created);
    updated.photo_directories.sort();
    if let Err(error) = persist(&updated) {
        if let Err(rollback_error) = fs::remove_dir(&target) {
            return Err(format!(
                "索引保存失败：{error}；清理新建目录失败：{rollback_error}"
            ));
        }
        return Err(format!("索引保存失败：{error}"));
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
    if !destination.starts_with(&parent) {
        return Err("目标文件夹超出当前照片目录".to_owned());
    }
    let destination_name = if roots.contains(&destination) {
        String::new()
    } else {
        destination
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| "无法读取目标子文件夹名称".to_owned())?
            .to_owned()
    };
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

/// 仅允许回收当前家庭已扫描的子文件夹；根目录和任何包含配置文件的目录均受保护。
pub fn find_photo_directory_for_deletion(
    index: &ResourceIndex,
    requested_path: &Path,
) -> Result<PathBuf, String> {
    let file_type = fs::symlink_metadata(requested_path)
        .map_err(|_| "子文件夹不存在，请重新扫描".to_owned())?
        .file_type();
    if !file_type.is_dir() || file_type.is_symlink() {
        return Err("只能删除普通子文件夹".to_owned());
    }
    let canonical = requested_path
        .canonicalize()
        .map_err(|_| "子文件夹不存在，请重新扫描".to_owned())?;
    let scanned_directory = index
        .photo_directories
        .iter()
        .any(|path| path == &canonical);
    let indexed_photo_below = index.resources.iter().any(|resource| {
        resource.kind == ResourceKind::Photo && resource.path.starts_with(&canonical)
    });
    if !scanned_directory && !indexed_photo_below {
        return Err("子文件夹不在当前家庭照片目录索引中，请重新扫描".to_owned());
    }
    let roots = canonical_photo_roots(index)?;
    if !roots
        .iter()
        .any(|root| canonical.starts_with(root) && &canonical != root)
    {
        return Err("不能删除已添加的照片根目录".to_owned());
    }
    if roots.iter().any(|root| root.starts_with(&canonical)) {
        return Err("该文件夹包含已添加的照片根目录，不能删除".to_owned());
    }
    let other_roots = index
        .roots
        .movies
        .iter()
        .chain(&index.roots.notes)
        .chain(&index.roots.exams);
    if other_roots
        .filter_map(|root| root.canonicalize().ok())
        .any(|root| root.starts_with(&canonical))
        || index.resources.iter().any(|resource| {
            resource.kind != ResourceKind::Photo && resource.path.starts_with(&canonical)
        })
    {
        return Err("该文件夹包含已登记的其他资源，不能从照片管理中删除".to_owned());
    }
    if contains_photo_metadata_file(&canonical)? {
        return Err("该文件夹包含照片配置 JSON，不能删除".to_owned());
    }
    Ok(canonical)
}

fn contains_photo_metadata_file(directory: &Path) -> Result<bool, String> {
    let mut pending = vec![directory.to_path_buf()];
    while let Some(current) = pending.pop() {
        for entry in fs::read_dir(current).map_err(|error| error.to_string())? {
            let entry = entry.map_err(|error| error.to_string())?;
            let file_type = entry.file_type().map_err(|error| error.to_string())?;
            if entry
                .file_name()
                .to_string_lossy()
                .eq_ignore_ascii_case(PHOTO_METADATA_FILE)
                || entry
                    .file_name()
                    .to_string_lossy()
                    .eq_ignore_ascii_case(LEGACY_PHOTO_METADATA_FILE)
            {
                return Ok(true);
            }
            if file_type.is_dir() && !file_type.is_symlink() {
                pending.push(entry.path());
            }
        }
    }
    Ok(false)
}

/// 目录已进入系统回收站后，只清理该目录及后代的照片索引。
pub fn remove_deleted_photo_directory(index: &mut ResourceIndex, directory: &Path) {
    let removed_ids: HashSet<String> = index
        .resources
        .iter()
        .filter(|resource| {
            resource.kind == ResourceKind::Photo && resource.path.starts_with(directory)
        })
        .map(|resource| resource.id.clone())
        .collect();
    index
        .resources
        .retain(|resource| !removed_ids.contains(&resource.id));
    index
        .metadata_by_id
        .retain(|id, _| !removed_ids.contains(id));
    index
        .photo_directories
        .retain(|path| !path.starts_with(directory));
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
