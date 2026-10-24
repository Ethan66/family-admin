import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ElRadioGroup, ElSelect } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import DailyTasksView from '@/views/DailyTasksView.vue'
import TaskCalendar from '@/components/TaskCalendar.vue'
import DailyTaskForm from '@/components/DailyTaskForm.vue'
import { formatTaskDate } from '@/utils/dailyTasks'

const { apiList, apiComplete, apiMembers, apiCreate } = vi.hoisted(() => ({ apiList: vi.fn(), apiComplete: vi.fn(), apiMembers: vi.fn(), apiCreate: vi.fn() }))
vi.mock('@/api/dailyTasks', () => ({ dailyTasksApi: { apiList, apiComplete, apiCreate } }))
vi.mock('@/api/modules', () => ({ transactionsApi: { members: apiMembers } }))
const date = formatTaskDate(new Date())
const task = { id: 1, title: '阅读 20 分钟', memberId: 7, memberName: '爸爸', taskDate: date, startDate: date, taskTime: '19:30:00', category: '学习', repeatDaily: true, completed: false, note: null }

describe('daily tasks interactions', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.resetAllMocks()
    apiList.mockResolvedValue([task])
    apiComplete.mockResolvedValue(undefined)
    apiMembers.mockResolvedValue([{ userId: 7, displayName: '爸爸' }, { userId: 8, displayName: '妈妈' }])
    apiCreate.mockResolvedValue(undefined)
  })
  it('loads the whole family by default, marks own occurrence and filters status', async () => {
    useAuthStore().user = { id: 7, familyId: 31, username: 'dad', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(DailyTasksView, { global: { renderStubDefaultSlot: true } })
    await flushPromises()
    expect(apiMembers).toHaveBeenCalledOnce()
    expect(apiList).toHaveBeenCalledWith(expect.objectContaining({ memberId: undefined }))
    const tempSelects = tempWrapper.findAllComponents(ElSelect)
    expect(tempSelects[0].findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual(['', 7, 8])
    await tempWrapper.find('.completion-toggle').trigger('click')
    await flushPromises()
    expect(apiComplete).toHaveBeenCalledWith(1, date, true)
    tempSelects[1].vm.$emit('update:modelValue', 'completed')
    await flushPromises()
    expect(tempWrapper.findComponent(TaskCalendar).props('tasks')).toEqual([])
    tempWrapper.unmount()
  })
  it('shows other family tasks in calendar and list without allowing edits or completion', async () => {
    useAuthStore().user = { id: 7, familyId: 31, username: 'dad', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempOtherTask = { ...task, id: 2, memberId: 8, memberName: '妈妈', title: '妈妈的阅读' }
    apiList.mockResolvedValue([task, tempOtherTask])
    const tempWrapper = shallowMount(DailyTasksView, { global: { renderStubDefaultSlot: true } })
    await flushPromises()
    expect(tempWrapper.findComponent(TaskCalendar).props('tasks')).toEqual([task, tempOtherTask])
    const tempOtherDetails = tempWrapper.findAll('.detail-task')[1]
    expect(tempOtherDetails.find('.completion-toggle').attributes('disabled')).toBeDefined()
    expect(tempOtherDetails.find('.task-menu').exists()).toBe(false)
    tempWrapper.findComponent(ElRadioGroup).vm.$emit('update:modelValue', 'list')
    await flushPromises()
    expect(tempWrapper.findAll('tbody tr')).toHaveLength(2)
    expect(tempWrapper.find('tbody').text()).toContain('妈妈的阅读')
    tempWrapper.unmount()
  })
  it('filters by another family member and can reset to all members', async () => {
    useAuthStore().user = { id: 7, familyId: 31, username: 'dad', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(DailyTasksView)
    await flushPromises()
    const tempMemberSelect = tempWrapper.findComponent(ElSelect)
    tempMemberSelect.vm.$emit('update:modelValue', 8)
    await flushPromises()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberId: 8 }))
    tempMemberSelect.vm.$emit('update:modelValue', '')
    await flushPromises()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberId: undefined }))
    tempWrapper.unmount()
  })
  it('passes family members to the form and submits a task assigned to another member', async () => {
    useAuthStore().user = { id: 7, familyId: 31, username: 'dad', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(DailyTasksView)
    await flushPromises()
    const tempForm = tempWrapper.findComponent(DailyTaskForm)
    expect(tempForm.props('members')).toEqual([{ userId: 7, displayName: '爸爸' }, { userId: 8, displayName: '妈妈' }])
    const tempPayload = { title: '阅读', memberId: 8, taskDate: date, taskTime: null, category: '', repeatDaily: false, note: null }
    tempForm.vm.$emit('submit', tempPayload)
    await flushPromises()
    expect(apiCreate).toHaveBeenCalledWith(tempPayload)
    tempWrapper.unmount()
  })
  it('does not add an administrator outside the selected family to member choices', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }
    tempAuth.selectFamily(31)
    const tempWrapper = shallowMount(DailyTasksView)
    await flushPromises()
    expect(tempWrapper.findComponent(DailyTaskForm).props('members')).toEqual([{ userId: 7, displayName: '爸爸' }, { userId: 8, displayName: '妈妈' }])
    tempWrapper.unmount()
  })
  it('discards a previous family response while loading another family', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }
    tempAuth.selectFamily(31)
    let tempResolve!: (value: typeof task[]) => void
    apiList.mockReturnValueOnce(new Promise((resolve) => { tempResolve = resolve })).mockResolvedValue([])
    const tempWrapper = shallowMount(DailyTasksView)
    await flushPromises()
    tempAuth.selectFamily(32)
    await flushPromises()
    tempResolve([task])
    await flushPromises()
    expect(tempWrapper.findComponent(TaskCalendar).props('tasks')).toEqual([])
    tempWrapper.unmount()
  })
})
