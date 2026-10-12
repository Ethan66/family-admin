import type { Role } from '@/types/api'
import type { ResourceEntry } from '@/types/resource'

export function canManageTransaction(role: Role, userId: number, createdBy: number): boolean {
  if (role === 'SUPER_ADMIN') return true
  return userId === createdBy
}

/** 将本机索引汇总为概览页四类资源计数。 */
export function countLocalResources(resources: ResourceEntry[]): {
  photos: number
  movies: number
  notes: number
  exams: number
} {
  return resources.reduce((counts, resource) => {
    if (resource.kind === 'PHOTO') counts.photos += 1
    if (resource.kind === 'MOVIE') counts.movies += 1
    if (resource.kind === 'NOTE') counts.notes += 1
    if (resource.kind === 'EXAM') counts.exams += 1
    return counts
  }, { photos: 0, movies: 0, notes: 0, exams: 0 })
}
