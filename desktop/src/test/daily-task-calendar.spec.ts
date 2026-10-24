import { describe, expect, it } from 'vitest'
import { buildCalendarDays, moveCalendarMonth } from '@/utils/dailyTasks'
import { shallowMount } from '@vue/test-utils'
import TaskCalendar from '@/components/TaskCalendar.vue'
import type { TaskOccurrence } from '@/types/dailyTask'

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

describe('daily task calendar previews', () => {
  const date = '2026-10-24'
  const task: TaskOccurrence = { id: 1, title: '已完成任务', memberId: 2, memberName: 'zc', taskDate: date, startDate: date, taskTime: null, category: '', repeatDaily: false, completed: true, note: null }

  it('shows three tasks with completed tasks last without changing the input order', () => {
    const tempTasks = [task, { ...task, id: 2, title: '待完成一', completed: false }, { ...task, id: 3, title: '待完成二', completed: false }]
    const tempWrapper = shallowMount(TaskCalendar, { props: { month: '2026-10', selectedDate: date, today: date, tasks: tempTasks } })
    const tempDay = tempWrapper.find(`[data-date="${date}"]`)
    expect(tempDay.findAll('.preview-title').map((item) => item.text())).toEqual(['待完成一', '待完成二', '已完成任务'])
    expect(tempDay.find('.more-tasks').exists()).toBe(false)
    expect(tempTasks.map((item) => item.id)).toEqual([1, 2, 3])
    tempWrapper.unmount()
  })

  it('shows only three pending tasks and counts the remaining completed task', () => {
    const tempTasks = [task, ...[2, 3, 4].map((id) => ({ ...task, id, title: `待完成${id}`, completed: false }))]
    const tempWrapper = shallowMount(TaskCalendar, { props: { month: '2026-10', selectedDate: date, today: date, tasks: tempTasks } })
    const tempDay = tempWrapper.find(`[data-date="${date}"]`)
    expect(tempDay.findAll('.preview-title').map((item) => item.text())).toEqual(['待完成2', '待完成3', '待完成4'])
    expect(tempDay.find('.more-tasks').text()).toBe('+ 1 项')
    tempWrapper.unmount()
  })
})
