import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TransactionsView from '@/views/TransactionsView.vue'

const { apiList, apiSummary } = vi.hoisted(() => ({ apiList: vi.fn(), apiSummary: vi.fn() }))

vi.mock('@/api/modules', () => ({ transactionsApi: { list: apiList, summary: apiSummary } }))

describe('transactions family context', () => {
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
