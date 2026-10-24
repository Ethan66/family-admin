/** 使用本地日期而非 UTC，避免北京时间凌晨切到前一天。 */
export function formatTaskDate(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** 按周一至周日补齐整月网格，包含相邻月份日期。 */
export function buildCalendarDays(month: string): string[] {
  const [tempYear, tempMonth] = month.split('-').map(Number)
  const tempStart = new Date(tempYear, tempMonth - 1, 1)
  tempStart.setDate(1 - (tempStart.getDay() + 6) % 7)
  const tempEnd = new Date(tempYear, tempMonth, 0)
  tempEnd.setDate(tempEnd.getDate() + (7 - tempEnd.getDay()) % 7)
  const tempDays: string[] = []
  for (const tempDate = new Date(tempStart); tempDate <= tempEnd; tempDate.setDate(tempDate.getDate() + 1)) tempDays.push(formatTaskDate(tempDate))
  return tempDays
}

/** 跨年切月仍返回固定的 YYYY-MM 格式。 */
export function moveCalendarMonth(month: string, offset: number): string {
  const [tempYear, tempMonth] = month.split('-').map(Number)
  return formatTaskDate(new Date(tempYear, tempMonth - 1 + offset, 1)).slice(0, 7)
}
