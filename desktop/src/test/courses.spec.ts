import { describe, expect, it } from 'vitest'
import { getCourseWeek, getCoursePeriod, moveCourseWeek } from '@/utils/courses'

describe('course calendar', () => {
  it('keeps Sunday in the week starting on Monday across a month boundary', () => {
    expect(getCourseWeek('2026-11-01')).toEqual(['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'])
  })
  it('moves by a full week across the year boundary', () => {
    expect(moveCourseWeek('2026-12-28', 1)).toBe('2027-01-04')
  })
  it('groups by start time, including noon in the afternoon', () => {
    expect(getCoursePeriod('11:30:00')).toBe('morning')
    expect(getCoursePeriod('12:00:00')).toBe('afternoon')
    expect(getCoursePeriod('18:00')).toBe('afternoon')
  })
})
