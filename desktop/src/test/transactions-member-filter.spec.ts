import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ElButton, ElSelect } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TransactionsView from '@/views/TransactionsView.vue'

const { apiList, apiSummary, apiMembers } = vi.hoisted(() => ({
  apiList: vi.fn(), apiSummary: vi.fn(), apiMembers: vi.fn(),
}))
vi.mock('@/api/modules', () => ({ transactionsApi: { list: apiList, summary: apiSummary, members: apiMembers } }))

describe('transaction member filter', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    apiList.mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    apiMembers.mockResolvedValue([{ userId: 2, displayName: '爸爸' }, { userId: 3, displayName: '妈妈' }])
  })

  it('only offers the current member and sends their name to both queries on initial load', async () => {
    useAuthStore().user = { id: 2, familyId: 12, username: 'member', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(TransactionsView, { global: { renderStubDefaultSlot: true } })
    await flushPromises()
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((select) => select.attributes('data-testid') === 'transaction-member-filter')!
    expect(tempSelect).toBeDefined()
    expect(tempSelect.props('modelValue')).toBe('爸爸')
    expect(tempSelect.props('clearable')).toBeFalsy()
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual(['爸爸'])
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberName: '爸爸', page: 1 }))
    expect(apiSummary).toHaveBeenLastCalledWith(expect.objectContaining({ memberName: '爸爸' }))
    tempWrapper.unmount()
  })

  it('offers all members to administrators, resets paging on search and clears the selection on family change', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }
    tempAuth.selectFamily(12)
    const tempWrapper = shallowMount(TransactionsView, { global: { renderStubDefaultSlot: true } })
    await flushPromises()
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((select) => select.attributes('data-testid') === 'transaction-member-filter')!
    expect(tempSelect).toBeDefined()
    expect(tempSelect.props('modelValue')).toBe('')
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual(['', '管理员', '爸爸', '妈妈'])
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: { filters: { page: number } } }).setupState
    tempState.filters.page = 3
    tempSelect.vm.$emit('update:modelValue', '妈妈')
    await nextTick()
    await tempWrapper.findAllComponents(ElButton).find((button) => button.text() === '查询')!.trigger('click')
    await flushPromises()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberName: '妈妈', page: 1 }))
    expect(apiSummary).toHaveBeenLastCalledWith(expect.objectContaining({ memberName: '妈妈' }))
    tempAuth.selectFamily(13)
    await flushPromises()
    expect(tempSelect.props('modelValue')).toBe('')
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberName: undefined, page: 1 }))
    tempWrapper.unmount()
  })
})
