import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ElSelect } from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TransactionsView from '@/views/TransactionsView.vue'

const { apiList, apiSummary, apiMembers, apiCreate, apiUpdate } = vi.hoisted(() => ({
  apiList: vi.fn(), apiSummary: vi.fn(), apiMembers: vi.fn(), apiCreate: vi.fn(), apiUpdate: vi.fn(),
}))
vi.mock('@/api/modules', () => ({ transactionsApi: {
  list: apiList, summary: apiSummary, members: apiMembers, create: apiCreate, update: apiUpdate,
} }))

function mountView() {
  return shallowMount(TransactionsView, { global: {
    renderStubDefaultSlot: true,
    stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } },
  } })
}

describe('transaction payment fields', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    apiList.mockResolvedValue({ records: [], total: 0 })
    apiSummary.mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 })
    apiMembers.mockResolvedValue([])
    apiCreate.mockResolvedValue({})
    apiUpdate.mockResolvedValue({})
    useAuthStore().user = { id: 2, familyId: 12, username: 'member', displayName: '爸爸', role: 'FAMILY_MEMBER' }
  })

  it('offers the specified choices in both form and filters', async () => {
    const tempWrapper = mountView()
    await tempWrapper.find('[data-testid="create-transaction"]').trigger('click')
    for (const [tempField, tempExpected] of [
      ['bank-card', ['工商', '农业', '交通', '光大', '招商', '支付宝', '微信']],
      ['platform', ['支付宝', '微信', '京东', '美团', '拼多多', '淘宝', '抖音']],
    ] as const) {
      for (const tempSuffix of ['', '-filter']) {
        const tempSelect = tempWrapper.findAllComponents(ElSelect).find((tempItem) => tempItem.attributes('data-testid') === `transaction-${tempField}${tempSuffix}`)!
        expect(tempSelect).toBeDefined()
        expect(tempSelect.props('clearable')).toBe(true)
        expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((tempItem) => tempItem.props('value'))).toEqual(tempExpected)
      }
    }
    tempWrapper.unmount()
  })

  it('sends both filters to list and summary and resets paging, then supports clearing', async () => {
    const tempWrapper = mountView()
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: {
      filters: { page: number; bankCard: string; platform: string }
      handleSearch: () => Promise<void>
    } }).setupState
    Object.assign(tempState.filters, { page: 3, bankCard: '招商', platform: '京东' })
    await tempState.handleSearch()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ bankCard: '招商', platform: '京东', page: 1 }))
    expect(apiSummary).toHaveBeenLastCalledWith(expect.objectContaining({ bankCard: '招商', platform: '京东' }))
    Object.assign(tempState.filters, { bankCard: '', platform: '' })
    await tempState.handleSearch()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ bankCard: undefined, platform: undefined }))
    expect(apiSummary).toHaveBeenLastCalledWith(expect.objectContaining({ bankCard: undefined, platform: undefined }))
    tempWrapper.unmount()
  })

  it('saves income selections, restores them for editing and clears them on a new record', async () => {
    const tempWrapper = mountView()
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: {
      transactionFormRef: { validate: () => Promise<boolean> }
      transactionForm: { type: string; bankCard: string | null; platform: string | null }
      handleCreate: () => void
      handleEdit: (record: object) => void
      handleSubmit: () => Promise<void>
    } }).setupState
    tempState.transactionFormRef = { validate: async () => true }
    tempState.handleCreate()
    Object.assign(tempState.transactionForm, { type: 'INCOME', bankCard: '工商', platform: '支付宝' })
    await tempState.handleSubmit()
    expect(apiCreate).toHaveBeenCalledWith(expect.objectContaining({ type: 'INCOME', bankCard: '工商', platform: '支付宝' }))
    tempState.handleEdit({ id: 9, type: 'INCOME', amount: 100, transactionDate: '2026-10-25', category: '工资', memberName: '爸爸', note: null, bankCard: '招商', platform: '微信' })
    expect(tempState.transactionForm).toMatchObject({ bankCard: '招商', platform: '微信' })
    Object.assign(tempState.transactionForm, { bankCard: '', platform: '' })
    await flushPromises()
    tempState.transactionFormRef = { validate: async () => true }
    await tempState.handleSubmit()
    expect(apiUpdate).toHaveBeenCalledWith(9, expect.objectContaining({ bankCard: '', platform: '' }))
    tempState.handleCreate()
    expect(tempState.transactionForm).toMatchObject({ bankCard: null, platform: null })
    await flushPromises()
    tempWrapper.unmount()
  })
})
