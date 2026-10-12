import { describe, expect, it } from 'vitest'
import { canManageTransaction, countLocalResources } from '@/utils/business'
import type { ResourceEntry } from '@/types/resource'

describe('business view rules', () => {
  it('allows the super administrator to manage every record', () => {
    expect(canManageTransaction('SUPER_ADMIN', 1, 2)).toBe(true)
  })

  it('allows a family member to manage only their own records', () => {
    expect(canManageTransaction('FAMILY_MEMBER', 2, 2)).toBe(true)
    expect(canManageTransaction('FAMILY_MEMBER', 2, 1)).toBe(false)
  })

  it('counts indexed resources by kind for the dashboard', () => {
    const resources = [
      { kind: 'PHOTO' },
      { kind: 'PHOTO' },
      { kind: 'MOVIE' },
      { kind: 'NOTE' },
    ] as ResourceEntry[]

    expect(countLocalResources(resources)).toEqual({ photos: 2, movies: 1, notes: 1, exams: 0 })
  })
})
