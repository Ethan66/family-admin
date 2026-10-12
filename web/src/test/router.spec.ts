import { describe, expect, it } from 'vitest'

import { routes } from '@/router'

describe('application routes', () => {
  it('contains every v1 business workspace', () => {
    const paths = JSON.stringify(routes)
    expect(paths).toContain('/dashboard')
    expect(paths).toContain('transactions')
    expect(paths).toContain('exams')
    expect(paths).toContain('photos')
    expect(paths).toContain('movies')
    expect(paths).toContain('notes')
    expect(paths).toContain('families')
  })
})
