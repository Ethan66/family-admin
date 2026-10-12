import type { Role } from '@/types/api'
import type { ResourceEntry, ResourceKind } from '@/types/resource'

const EXTENSIONS: Record<ResourceKind, readonly string[]> = {
  PHOTO: ['.jpg', '.jpeg', '.png', '.webp', '.gif'],
  MOVIE: ['.mp4', '.mkv', '.avi', '.rmvb'],
  NOTE: ['.md', '.markdown', '.txt'],
  EXAM: ['.jpg', '.jpeg', '.png', '.webp', '.gif', '.pdf'],
}

export function getAllowedExtensions(kind: ResourceKind): string[] {
  return [...EXTENSIONS[kind]]
}

/** 搜索仅匹配索引中的文件名和路径，不读取源文件。 */
export function filterResources(resources: ResourceEntry[], keyword: string): ResourceEntry[] {
  const tempKeyword = keyword.trim().toLocaleLowerCase()
  if (!tempKeyword) return resources
  return resources.filter((resource) => {
    return resource.name.toLocaleLowerCase().includes(tempKeyword)
      || resource.path.toLocaleLowerCase().includes(tempKeyword)
  })
}

export function formatFileSize(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${Number((size / 1024).toFixed(1))} KB`
  if (size < 1024 * 1024 * 1024) return `${Number((size / 1024 / 1024).toFixed(1))} MB`
  return `${Number((size / 1024 / 1024 / 1024).toFixed(1))} GB`
}

export function resolveActiveFamilyId(
  user: { role: Role; familyId: number | null },
  selectedFamilyId: number | null,
): number | null {
  return user.role === 'SUPER_ADMIN' ? selectedFamilyId : user.familyId
}
