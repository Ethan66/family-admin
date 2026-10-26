import { createPinia, setActivePinia } from 'pinia'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import TransactionsView from '@/views/TransactionsView.vue'
import type { TransactionRecord } from '@/types/api'

vi.mock('@/api/modules', () => ({ transactionsApi: {
  list: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  summary: vi.fn().mockResolvedValue({ income: 0, expense: 0, balance: 0, incomeCount: 0, expenseCount: 0 }),
  members: vi.fn().mockResolvedValue([]),
} }))

describe('transaction member permissions', () => {
  it('allows the selected member, creator and administrator while denying unrelated members', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 7, familyId: 31, username: 'zc', displayName: 'zc', role: 'FAMILY_MEMBER' }
    const tempWrapper = shallowMount(TransactionsView)
    await flushPromises()
    const tempState = (tempWrapper.vm.$ as unknown as { setupState: {
      canManage: (record: Pick<TransactionRecord, 'createdBy' | 'memberName'>) => boolean
    } }).setupState
    expect(tempState.canManage({ createdBy: 8, memberName: 'zc' })).toBe(true)
    expect(tempState.canManage({ createdBy: 7, memberName: 'xi' })).toBe(true)
    expect(tempState.canManage({ createdBy: 8, memberName: 'xi' })).toBe(false)
    tempAuthStore.user.role = 'SUPER_ADMIN'
    expect(tempState.canManage({ createdBy: 8, memberName: 'xi' })).toBe(true)
    tempWrapper.unmount()
  })
})
