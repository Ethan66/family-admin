import { describe, expect, it } from 'vitest'
import { filterResources, formatFileSize, getAllowedExtensions, resolveActiveFamilyId } from '@/utils/resource'
import type { ResourceEntry } from '@/types/resource'

const PHOTO: ResourceEntry = {
  id: 'photo-1',
  kind: 'PHOTO',
  path: 'D:\\Family\\Photos\\Birthday.JPG',
  name: 'Birthday.JPG',
  extension: '.jpg',
  size: 1_572_864,
  modifiedAt: '2026-09-24T08:00:00+08:00',
}

describe('resource helpers', () => {
  it('returns the exact extension allowlist for every resource kind', () => {
    expect(getAllowedExtensions('PHOTO')).toEqual(['.jpg', '.jpeg', '.png', '.webp', '.gif'])
    expect(getAllowedExtensions('MOVIE')).toEqual(['.mp4', '.mkv', '.avi', '.rmvb'])
    expect(getAllowedExtensions('NOTE')).toEqual(['.md', '.markdown', '.txt'])
    expect(getAllowedExtensions('EXAM')).toEqual(['.jpg', '.jpeg', '.png', '.webp', '.gif', '.pdf'])
  })

  it('filters resources by file name and path without case sensitivity', () => {
    expect(filterResources([PHOTO], 'birthday')).toEqual([PHOTO])
    expect(filterResources([PHOTO], 'family\\photos')).toEqual([PHOTO])
    expect(filterResources([PHOTO], 'movie')).toEqual([])
  })

  it('formats byte sizes for compact list display', () => {
    expect(formatFileSize(0)).toBe('0 B')
    expect(formatFileSize(1024)).toBe('1 KB')
    expect(formatFileSize(1_572_864)).toBe('1.5 MB')
  })

  it('uses the account family and requires super administrators to select one', () => {
    expect(resolveActiveFamilyId({ role: 'FAMILY_MEMBER', familyId: 12 }, null)).toBe(12)
    expect(resolveActiveFamilyId({ role: 'SUPER_ADMIN', familyId: null }, 21)).toBe(21)
    expect(resolveActiveFamilyId({ role: 'SUPER_ADMIN', familyId: null }, null)).toBeNull()
  })
})
