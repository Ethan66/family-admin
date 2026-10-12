import type { ResourceEntry, ResourceIndex } from '@/types/resource'

export interface PhotoDirectoryNode {
  path: string
  name: string
  count: number
  children: PhotoDirectoryNode[]
}

function slashedPhotoPath(path: string): string {
  const tempSlashedPath = path.replace(/\\/g, '/')
  if (tempSlashedPath.toLowerCase().startsWith('//?/unc/')) return `//${tempSlashedPath.slice(8)}`
  if (tempSlashedPath.startsWith('//?/')) return tempSlashedPath.slice(4)
  return tempSlashedPath
}

function normalizePhotoPath(path: string): string {
  return (slashedPhotoPath(path).replace(/\/+$/, '') || '/').toLowerCase()
}

/** 目录筛选必须按完整路径段比较，不能把 trip2 误判为 trip 的子目录。 */
export function isPhotoInsideDirectory(photoPath: string, directoryPath: string): boolean {
  const tempPhotoPath = normalizePhotoPath(photoPath)
  const tempDirectoryPath = normalizePhotoPath(directoryPath)
  return tempPhotoPath === tempDirectoryPath
    || tempPhotoPath.startsWith(`${tempDirectoryPath === '/' ? '' : tempDirectoryPath}/`)
}

function photoDirectoryOf(path: string): string {
  const tempSeparatorIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
  return path.slice(0, tempSeparatorIndex)
}

/** 以扫描目录为权威；旧索引尚未重扫时，用照片路径补出非空祖先目录。 */
export function buildPhotoDirectoryTree(index: ResourceIndex): PhotoDirectoryNode[] {
  const tempPhotos = index.resources.filter((resource): resource is ResourceEntry => resource.kind === 'PHOTO')
  return index.roots.photos.map((rootPath) => {
    const tempRootPath = normalizePhotoPath(rootPath)
    const tempRootSlashedPath = slashedPhotoPath(rootPath).replace(/\/+$/, '')
    const tempSeparator = rootPath.includes('\\') ? '\\' : '/'
    const tempRootNode: PhotoDirectoryNode = {
      path: rootPath,
      name: tempRootSlashedPath.split('/').pop() || rootPath,
      count: 0,
      children: [],
    }
    const tempNodes = new Map<string, PhotoDirectoryNode>([[tempRootPath, tempRootNode]])

    function addDirectory(directoryPath: string): void {
      if (!isPhotoInsideDirectory(directoryPath, rootPath)) return
      const tempSlashedPath = slashedPhotoPath(directoryPath)
      const tempRelativePath = tempSlashedPath.slice(tempRootSlashedPath.length).replace(/^\/+/, '')
      let tempParentNode = tempRootNode
      let tempCurrentPath = rootPath.replace(/[\\/]+$/, '')
      for (const tempName of tempRelativePath.split('/').filter(Boolean)) {
        tempCurrentPath = `${tempCurrentPath}${tempSeparator}${tempName}`
        const tempKey = normalizePhotoPath(tempCurrentPath)
        let tempNode = tempNodes.get(tempKey)
        if (!tempNode) {
          tempNode = { path: tempCurrentPath, name: tempName, count: 0, children: [] }
          tempNodes.set(tempKey, tempNode)
          tempParentNode.children.push(tempNode)
        }
        tempParentNode = tempNode
      }
    }

    for (const tempDirectoryPath of index.photoDirectories) addDirectory(tempDirectoryPath)
    for (const tempPhoto of tempPhotos) addDirectory(photoDirectoryOf(tempPhoto.path))

    function calculateCounts(node: PhotoDirectoryNode): void {
      node.count = tempPhotos.filter((resource) => isPhotoInsideDirectory(resource.path, node.path)).length
      node.children.sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
      for (const tempChild of node.children) calculateCounts(tempChild)
    }
    calculateCounts(tempRootNode)
    return tempRootNode
  })
}
