import type { ResourceEntry } from '@/types/resource'

export interface PhotoWallRow {
  height: number
  items: Array<{ resource: ResourceEntry; width: number }>
}

const DEFAULT_PHOTO_ASPECT_RATIO = 4 / 3

function photoAspectRatio(resource: ResourceEntry): number {
  if (!resource.imageWidth || !resource.imageHeight) return DEFAULT_PHOTO_ASPECT_RATIO
  return resource.imageWidth / resource.imageHeight
}

/** 完整行等比铺满容器；最后一行维持目标高度并左对齐，超宽时才缩小。 */
export function buildPhotoRows(resources: ResourceEntry[], containerWidth: number, targetHeight: number, gap: number): PhotoWallRow[] {
  const tempRows: PhotoWallRow[] = []
  const tempWidth = Math.max(1, containerWidth)
  let tempResources: ResourceEntry[] = []
  let tempAspectSum = 0

  for (const tempResource of resources) {
    tempResources.push(tempResource)
    tempAspectSum += photoAspectRatio(tempResource)
    if (tempAspectSum * targetHeight + gap * (tempResources.length - 1) < tempWidth) continue

    const tempHeight = Math.max(1, (tempWidth - gap * (tempResources.length - 1)) / tempAspectSum)
    tempRows.push({ height: tempHeight, items: tempResources.map((resource) => ({ resource, width: photoAspectRatio(resource) * tempHeight })) })
    tempResources = []
    tempAspectSum = 0
  }

  if (tempResources.length) {
    const tempHeight = Math.min(targetHeight, Math.max(1, (tempWidth - gap * (tempResources.length - 1)) / tempAspectSum))
    tempRows.push({ height: tempHeight, items: tempResources.map((resource) => ({ resource, width: photoAspectRatio(resource) * tempHeight })) })
  }
  return tempRows
}
