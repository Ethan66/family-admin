use std::fs;
use std::io::BufReader;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

use chrono::{DateTime, NaiveDateTime, Utc};
use exif::{In, Tag, Value};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

#[derive(Clone, Copy, Debug, Deserialize, Eq, PartialEq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ResourceKind {
    Photo,
    Movie,
    Note,
    Exam,
}

impl ResourceKind {
    pub fn allowed_extensions(self) -> &'static [&'static str] {
        match self {
            Self::Photo => &[".jpg", ".jpeg", ".png", ".webp", ".gif"],
            Self::Movie => &[".mp4", ".mkv", ".avi", ".rmvb"],
            Self::Note => &[".md", ".markdown", ".txt"],
            Self::Exam => &[".jpg", ".jpeg", ".png", ".webp", ".gif", ".pdf"],
        }
    }
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ResourceEntry {
    pub id: String,
    pub kind: ResourceKind,
    pub path: PathBuf,
    pub name: String,
    pub extension: String,
    pub size: u64,
    pub modified_at: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub captured_at: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub image_width: Option<u32>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub image_height: Option<u32>,
}

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ScanWarning {
    pub path: String,
    pub reason: String,
}

#[derive(Debug)]
pub struct ScanResult {
    pub resources: Vec<ResourceEntry>,
    pub directories: Vec<PathBuf>,
    pub warnings: Vec<ScanWarning>,
}

pub fn scan_roots(kind: ResourceKind, roots: &[PathBuf]) -> ScanResult {
    let mut result = ScanResult {
        resources: Vec::new(),
        directories: Vec::new(),
        warnings: Vec::new(),
    };
    for root in roots {
        match root.canonicalize() {
            Ok(canonical_root) if canonical_root.is_dir() => {
                visit_directory(kind, &canonical_root, &mut result);
            }
            Ok(_) => result.warnings.push(warning(root, "路径不是文件夹")),
            Err(error) => result.warnings.push(warning(root, &error.to_string())),
        }
    }
    result
        .resources
        .sort_by(|left, right| left.path.cmp(&right.path));
    result.directories.sort();
    result.directories.dedup();
    result
}

fn visit_directory(kind: ResourceKind, directory: &Path, result: &mut ScanResult) {
    let entries = match fs::read_dir(directory) {
        Ok(entries) => entries,
        Err(error) => {
            result.warnings.push(warning(directory, &error.to_string()));
            return;
        }
    };
    if kind == ResourceKind::Photo {
        result.directories.push(directory.to_path_buf());
    }

    for entry_result in entries {
        let entry = match entry_result {
            Ok(entry) => entry,
            Err(error) => {
                result.warnings.push(warning(directory, &error.to_string()));
                continue;
            }
        };
        let path = entry.path();
        let file_type = match entry.file_type() {
            Ok(file_type) => file_type,
            Err(error) => {
                result.warnings.push(warning(&path, &error.to_string()));
                continue;
            }
        };
        if file_type.is_symlink() {
            continue;
        }
        if file_type.is_dir() {
            visit_directory(kind, &path, result);
            continue;
        }
        if !file_type.is_file() || !is_supported(kind, &path) {
            continue;
        }
        match create_resource_entry(kind, &path) {
            Ok(resource) => result.resources.push(resource),
            Err(error) => result.warnings.push(warning(&path, &error.to_string())),
        }
    }
}

fn is_supported(kind: ResourceKind, path: &Path) -> bool {
    let extension = match path.extension().and_then(|value| value.to_str()) {
        Some(value) => format!(".{}", value.to_lowercase()),
        None => return false,
    };
    kind.allowed_extensions().contains(&extension.as_str())
}

pub(crate) fn create_resource_entry(
    kind: ResourceKind,
    path: &Path,
) -> std::io::Result<ResourceEntry> {
    let canonical_path = path.canonicalize()?;
    let metadata = fs::metadata(&canonical_path)?;
    let extension = canonical_path
        .extension()
        .and_then(|value| value.to_str())
        .map(|value| format!(".{}", value.to_lowercase()))
        .unwrap_or_default();
    let name = canonical_path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or_default()
        .to_owned();
    let modified = metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH);
    let modified_at: DateTime<Utc> = modified.into();
    let captured_at = if kind == ResourceKind::Photo {
        read_photo_capture_time(&canonical_path)
    } else {
        None
    };
    let image_dimensions = if kind == ResourceKind::Photo {
        imagesize::size(&canonical_path).ok()
    } else {
        None
    };
    let image_width = image_dimensions
        .as_ref()
        .and_then(|dimensions| u32::try_from(dimensions.width).ok());
    let image_height = image_dimensions
        .as_ref()
        .and_then(|dimensions| u32::try_from(dimensions.height).ok());
    let mut hasher = Sha256::new();
    hasher.update(format!(
        "{kind:?}:{}",
        canonical_path.to_string_lossy().to_lowercase()
    ));

    Ok(ResourceEntry {
        id: hex::encode(hasher.finalize()),
        kind,
        path: canonical_path,
        name,
        extension,
        size: metadata.len(),
        modified_at: modified_at.to_rfc3339(),
        captured_at,
        image_width,
        image_height,
    })
}

/// EXIF 时间通常没有时区；保留相机记录的本地年月日时分秒供照片墙排序。
fn read_photo_capture_time(path: &Path) -> Option<String> {
    let file = fs::File::open(path).ok()?;
    let exif = exif::Reader::new()
        .read_from_container(&mut BufReader::new(file))
        .ok()?;
    let field = exif.get_field(Tag::DateTimeOriginal, In::PRIMARY)?;
    let Value::Ascii(values) = &field.value else {
        return None;
    };
    let value = values.first()?;
    let timestamp = std::str::from_utf8(value).ok()?.trim_end_matches('\0');
    NaiveDateTime::parse_from_str(timestamp, "%Y:%m:%d %H:%M:%S")
        .ok()
        .map(|datetime| datetime.format("%Y-%m-%dT%H:%M:%S").to_string())
}

fn warning(path: &Path, reason: &str) -> ScanWarning {
    ScanWarning {
        path: path.to_string_lossy().into_owned(),
        reason: reason.to_owned(),
    }
}
