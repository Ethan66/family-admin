import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ElButton, ElSelect } from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TransactionsView from '@/views/TransactionsView.vue'

const { apiList, apiSummary, apiMembers, apiCreate } = vi.hoisted(() => ({ apiList: vi.fn(), apiSummary: vi.fn(), apiMembers: vi.fn(), apiCreate: vi.fn() }))

vi.mock('@/api/modules', () => ({ transactionsApi: { list: apiList, summary: apiSummary, members: apiMembers, create: apiCreate } }))

describe('transactions family context', () => {
  it('keeps the current user selectable and allows saving while the member request is pending', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    apiList.mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    let tempResolveMembers!: (members: Array<{ userId: number; displayName: string }>) => void
    apiMembers.mockReturnValue(new Promise((resolve) => { tempResolveMembers = resolve }))
    apiCreate.mockReset().mockResolvedValue({})
    useAuthStore().user = { id: 2, familyId: 12, username: 'member', displayName: '爸爸', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(TransactionsView, { global: {
      renderStubDefaultSlot: true,
      stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } },
    } })
    await tempWrapper.find('[data-testid="create-transaction"]').trigger('click')
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((select) => select.attributes('data-testid') === 'transaction-member')!
    expect(tempSelect.props('disabled')).toBeFalsy()
    expect(tempSelect.props('loading')).toBeFalsy()
    expect(tempSelect.props('modelValue')).toBe('爸爸')
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual(['爸爸'])
    expect(tempWrapper.find('[role="status"]').text()).toContain('加载')
    const tempSaveButton = tempWrapper.findAllComponents(ElButton).find((button) => button.text() === '保存')!
    expect(tempSaveButton.props('disabled')).toBeFalsy()
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: {
      transactionFormRef: { validate: () => Promise<boolean> }
      handleSubmit: () => Promise<void>
    } }).setupState
    tempState.transactionFormRef = { validate: async () => true }
    await tempState.handleSubmit()
    expect(apiCreate).toHaveBeenCalledWith(expect.objectContaining({ memberName: '爸爸' }))
    tempResolveMembers([])
    await flushPromises()
    tempWrapper.unmount()
  })
  it('offers family members in a select and defaults new records to the current user', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    apiList.mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    apiMembers.mockResolvedValue([{ userId: 3, displayName: '妈妈' }])
    useAuthStore().user = { id: 1, familyId: null, username: 'admin', displayName: '超级管理员', role: 'SUPER_ADMIN' }
    useAuthStore().selectFamily(12)
    const tempWrapper = shallowMount(TransactionsView, { global: { renderStubDefaultSlot: true } })
    await tempWrapper.find('[data-testid="create-transaction"]').trigger('click')
    await flushPromises()
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((select) => select.attributes('data-testid') === 'transaction-member')!
    expect(tempSelect).toBeDefined()
    expect(tempSelect.props('modelValue')).toBe('超级管理员')
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual(['超级管理员', '妈妈'])
    tempSelect.vm.$emit('update:modelValue', '妈妈')
    await nextTick()
    expect(tempSelect.props('modelValue')).toBe('妈妈')
    await tempWrapper.find('[data-testid="create-transaction"]').trigger('click')
    await flushPromises()
    expect(tempSelect.props('modelValue')).toBe('超级管理员')
    tempWrapper.unmount()
  })

  it('preserves historical member names when editing and ignores member responses after switching families', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    apiList.mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    let tempResolveMembers!: (members: Array<{ userId: number; displayName: string }>) => void
    const tempMembersPromise = new Promise<Array<{ userId: number; displayName: string }>>((resolve) => { tempResolveMembers = resolve })
    apiMembers.mockReset().mockReturnValueOnce(tempMembersPromise).mockReturnValueOnce(tempMembersPromise).mockResolvedValue([])
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 1, familyId: null, username: 'admin', displayName: '超级管理员', role: 'SUPER_ADMIN' }
    tempAuthStore.selectFamily(12)
    const tempWrapper = shallowMount(TransactionsView, { global: { renderStubDefaultSlot: true } })
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: {
      handleEdit: (record: object) => void
      transactionForm: { memberName: string }
      membersData: Array<{ userId: number; displayName: string }>
      isDialogVisible: boolean
    } }).setupState
    tempState.handleEdit({ id: 9, type: 'EXPENSE', amount: 12, transactionDate: '2026-10-23', category: '餐饮', memberName: '原成员', note: null })
    await nextTick()
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((select) => select.attributes('data-testid') === 'transaction-member')!
    expect(tempSelect.props('modelValue')).toBe('原成员')
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).find((option) => option.props('value') === '原成员')?.props('disabled')).toBe(true)
    tempAuthStore.selectFamily(13)
    await nextTick()
    tempResolveMembers([{ userId: 3, displayName: '上一个家庭成员' }])
    await flushPromises()
    expect(tempState.isDialogVisible).toBe(false)
    expect(tempState.membersData).toEqual([])
    tempWrapper.unmount()
  })
  it('waits for a super administrator to select a family before loading records', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    apiList.mockReset().mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockReset().mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }

    const tempWrapper = shallowMount(TransactionsView)
    expect(apiList).not.toHaveBeenCalled()
    expect(apiSummary).not.toHaveBeenCalled()
    expect(tempWrapper.find('el-alert-stub').exists()).toBe(false)
    expect(tempWrapper.find('[data-testid="create-transaction"]').exists()).toBe(false)

    tempAuthStore.selectFamily(12)
    await nextTick()
    expect(apiList).toHaveBeenCalledOnce()
    expect(apiSummary).toHaveBeenCalledOnce()
    expect(tempWrapper.find('[data-testid="create-transaction"]').exists()).toBe(true)
    tempWrapper.unmount()
  })

  it('defaults the type filter to all without adding all to the transaction form', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    apiList.mockReset().mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockReset().mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 2, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' }

    const tempWrapper = shallowMount(TransactionsView)
    await nextTick()
    expect(apiList).toHaveBeenCalledWith(expect.objectContaining({ type: undefined }))
    expect(apiSummary).toHaveBeenCalledWith(expect.objectContaining({ type: undefined }))
    const tempInstance = tempWrapper.vm.$ as unknown as {
      setupState: {
        filters: { type: string }
        options: {
          filterTypes: Array<{ label: string; value: string }>
          types: Array<{ label: string; value: string }>
        }
      }
    }
    expect(tempInstance.setupState.filters.type).toBe('')
    expect(tempInstance.setupState.options.filterTypes).toEqual([
      { label: '全部', value: '' },
      { label: '收入', value: 'INCOME' },
      { label: '支出', value: 'EXPENSE' },
    ])
    expect(tempInstance.setupState.options.types).toEqual([
      { label: '收入', value: 'INCOME' },
      { label: '支出', value: 'EXPENSE' },
    ])
    tempWrapper.unmount()
  })

  it('defaults a newly created transaction date to today', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 9, 4, 12))
    try {
      sessionStorage.clear()
      setActivePinia(createPinia())
      apiList.mockReset().mockResolvedValue({ records: [], total: 0 })
      apiSummary.mockReset().mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
      const tempAuthStore = useAuthStore()
      tempAuthStore.user = { id: 2, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' }

      const tempWrapper = shallowMount(TransactionsView)
      await tempWrapper.find('[data-testid="create-transaction"]').trigger('click')
      const tempInstance = tempWrapper.vm.$ as unknown as { setupState: { transactionForm: { transactionDate: string } } }
      expect(tempInstance.setupState.transactionForm.transactionDate).toBe('2026-10-04')
      tempWrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })
})
