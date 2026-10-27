import { describe, expect, it } from 'vitest'
import { routes } from '@/router'

describe('desktop routes', () => {
  it('exposes local resource pages under the authenticated shell', () => {
    const shellRoute = routes.find((route) => route.path === '/')
    const childPaths = shellRoute?.children?.map((route) => route.path)

    expect(childPaths).toEqual(expect.arrayContaining(['photos', 'movies', 'notes', 'quick-notes', 'exams', 'daily-tasks', 'passbooks']))
  })

  it('protects administrator pages with the super administrator role', () => {
    const shellRoute = routes.find((route) => route.path === '/')
    const familiesRoute = shellRoute?.children?.find((route) => route.path === 'families')
    const auditRoute = shellRoute?.children?.find((route) => route.path === 'audit')

    expect(familiesRoute?.meta?.superAdmin).toBe(true)
    expect(auditRoute?.meta?.superAdmin).toBe(true)
  })
})
