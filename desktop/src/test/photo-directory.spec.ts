import { describe, expect, it } from 'vitest'
import { buildPhotoDirectoryTree, isPhotoInsideDirectory } from '@/utils/photoDirectory'
import type { ResourceEntry, ResourceIndex } from '@/types/resource'

function photo(id: string, path: string): ResourceEntry {
  return { id, kind: 'PHOTO', path, name: `${id}.jpg`, extension: '.jpg', size: 1, modifiedAt: '2026-10-11T00:00:00Z' }
}

function photoIndex(): ResourceIndex {
  return {
    schemaVersion: 2,
    familyId: 12,
    roots: { photos: ['C:\\photos', 'D:\\family'], movies: [], notes: [], exams: [] },
    lastScannedAt: null,
    resources: [
      photo('a', 'C:\\photos\\trip\\a.jpg'),
      photo('b', 'C:\\photos\\trip\\child\\b.jpg'),
      photo('c', 'C:\\photos\\trip2\\c.jpg'),
    ],
    photoDirectories: [
      'C:\\photos', 'C:\\photos\\trip', 'C:\\photos\\trip\\child',
      'C:\\photos\\trip2', 'C:\\photos\\empty', 'D:\\family',
    ],
    warnings: [],
    metadataById: {},
  }
}

describe('photo directory tree', () => {
  it('includes nested and empty folders under separate roots with descendant counts', () => {
    const tempTree = buildPhotoDirectoryTree(photoIndex())

    expect(tempTree).toHaveLength(2)
    expect(tempTree[0].count).toBe(3)
    const tempTrip = tempTree[0].children.find((directory) => directory.name === 'trip')
    expect(tempTrip?.count).toBe(2)
    expect(tempTrip?.path).toBe('C:\\photos\\trip')
    expect(tempTrip?.children[0].name).toBe('child')
    expect(tempTrip?.children[0].count).toBe(1)
    expect(tempTree[0].children.find((directory) => directory.name === 'empty')?.count).toBe(0)
    expect(tempTree[1].count).toBe(0)
  })

  it('compares complete path segments rather than matching a similarly named folder', () => {
    expect(isPhotoInsideDirectory('C:\\photos\\trip\\a.jpg', 'C:\\photos\\trip')).toBe(true)
    expect(isPhotoInsideDirectory('C:\\photos\\trip2\\a.jpg', 'C:\\photos\\trip')).toBe(false)
  })

  it('reconstructs non-empty ancestors from an older index before the first rescan', () => {
    const tempIndex = photoIndex()
    tempIndex.photoDirectories = []

    const tempTree = buildPhotoDirectoryTree(tempIndex)

    expect(tempTree[0].children.map((directory) => directory.name)).toEqual(['trip', 'trip2'])
    expect(tempTree[0].children[0].children[0].name).toBe('child')
  })

  it('matches Windows canonical paths with an extended-length prefix to a selected root', () => {
    const tempIndex = photoIndex()
    tempIndex.roots.photos = ['D:\\photos']
    tempIndex.resources = [photo('a', '\\\\?\\D:\\photos\\trip\\a.jpg')]
    tempIndex.photoDirectories = ['\\\\?\\D:\\photos', '\\\\?\\D:\\photos\\trip']

    const tempTree = buildPhotoDirectoryTree(tempIndex)

    expect(tempTree[0].children[0].name).toBe('trip')
    expect(tempTree[0].children[0].count).toBe(1)
    expect(isPhotoInsideDirectory(tempIndex.resources[0].path, 'D:\\photos\\trip')).toBe(true)
  })
})
