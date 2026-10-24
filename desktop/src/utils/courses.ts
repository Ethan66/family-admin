import { formatTaskDate } from '@/utils/dailyTasks'

/** 使用本地日期计算周一，避免 UTC 转换导致日期偏移。 */
export function getCourseWeek(date: string): string[] {
  const tempDate = new Date(`${date}T12:00:00`)
  tempDate.setDate(tempDate.getDate() - (tempDate.getDay() + 6) % 7)
  return Array.from({ length: 7 }, (_, index) => {
    const tempDay = new Date(tempDate)
    tempDay.setDate(tempDay.getDate() + index)
    return formatTaskDate(tempDay)
  })
}
export function moveCourseWeek(date: string, offset: number): string {
  const tempDate = new Date(`${date}T12:00:00`)
  tempDate.setDate(tempDate.getDate() + offset * 7)
  return formatTaskDate(tempDate)
}
export function getCoursePeriod(time: string): 'morning' | 'afternoon' {
  return time < '12:00' ? 'morning' : 'afternoon'
}
