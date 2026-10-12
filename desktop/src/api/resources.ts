import { invoke } from '@tauri-apps/api/core'
import { open } from '@tauri-apps/plugin-dialog'
import type { PreviewData, ResourceIndex, ResourceKind, ResourceMetadata } from '@/types/resource'

function ensureDesktopRuntime(): void {
  if (!('__TAURI_INTERNALS__' in window)) {
    throw new Error('目录扫描仅可在桌面应用中使用')
  }
}

/** 使用系统目录选择器返回原生绝对路径，不读取目录内容。 */
export async function selectResourceDirectories(): Promise<string[]> {
  ensureDesktopRuntime()
  const res = await open({ directory: true, multiple: true, title: '选择需要扫描的文件夹' })
  if (!res) return []
  return Array.isArray(res) ? res : [res]
}

export async function loadResourceIndex(familyId: number): Promise<ResourceIndex> {
  ensureDesktopRuntime()
  return invoke<ResourceIndex>('load_resource_index', { familyId })
}

export async function scanResources(
  familyId: number,
  kind: ResourceKind,
  roots: string[],
): Promise<ResourceIndex> {
  ensureDesktopRuntime()
  return invoke<ResourceIndex>('scan_resources', { familyId, kind, roots })
}

export async function saveResourceMetadata(
  familyId: number,
  resourceId: string,
  metadata: ResourceMetadata,
): Promise<ResourceIndex> {
  ensureDesktopRuntime()
  return invoke<ResourceIndex>('save_resource_metadata', { familyId, resourceId, metadata })
}

/** 只请求回收当前家庭已索引的照片，原文件由桌面端移入系统回收站。 */
export async function recyclePhoto(familyId: number, resourceId: string): Promise<ResourceIndex> {
  ensureDesktopRuntime()
  return invoke<ResourceIndex>('recycle_photo', { familyId, resourceId })
}

/** 整批移动已索引照片到当前家庭照片目录下的子文件夹。 */
export async function movePhotos(
  familyId: number,
  resourceIds: string[],
  destinationParent: string,
  newFolderName: string | null,
): Promise<ResourceIndex> {
  ensureDesktopRuntime()
  return invoke<ResourceIndex>('move_photos', { familyId, resourceIds, destinationParent, newFolderName })
}

/** 文本内容由 Rust 在家庭索引和 2 MB 上限校验后返回。 */
export async function readTextPreview(familyId: number, path: string): Promise<string> {
  ensureDesktopRuntime()
  return invoke<string>('read_text_preview', { familyId, path })
}

export async function readBinaryPreview(familyId: number, path: string): Promise<PreviewData> {
  ensureDesktopRuntime()
  return invoke<PreviewData>('read_binary_preview', { familyId, path })
}

export async function openWithDefaultApp(familyId: number, path: string): Promise<void> {
  ensureDesktopRuntime()
  await invoke('open_with_default_app', { familyId, path })
}

export async function revealInExplorer(familyId: number, path: string): Promise<void> {
  ensureDesktopRuntime()
  await invoke('reveal_in_explorer', { familyId, path })
}
