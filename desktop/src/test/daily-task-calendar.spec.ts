import { describe, expect, it } from 'vitest'
import { buildCalendarDays, moveCalendarMonth } from '@/utils/dailyTasks'

describe('daily task calendar dates', () => {
  it('uses Monday-first complete weeks including adjacent months', () => {
    const tempDays = buildCalendarDays('2026-10')
    expect(tempDays).toHaveLength(35)
    expect(tempDays[0]).toBe('2026-09-28')
    expect(tempDays[34]).toBe('2026-11-01')
    expect(tempDays.filter((day) => day.startsWith('2026-10'))).toHaveLength(31)
  })
  it('handles leap February, six-week months and year transitions', () => {
    expect(buildCalendarDays('2024-02')).toContain('2024-02-29')
    expect(buildCalendarDays('2026-03')).toHaveLength(42)
    expect(moveCalendarMonth('2026-12', 1)).toBe('2027-01')
    expect(moveCalendarMonth('2026-01', -1)).toBe('2025-12')
  })
})
