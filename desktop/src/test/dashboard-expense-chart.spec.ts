import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import DashboardView from '@/views/DashboardView.vue'

const { apiOverview, apiTransactions, apiResourceIndex } = vi.hoisted(() => ({
  apiOverview: vi.fn(),
  apiTransactions: vi.fn(),
  apiResourceIndex: vi.fn(),
}))

vi.mock('@/api/modules', () => ({
  dashboardApi: { overview: apiOverview },
  transactionsApi: { list: apiTransactions },
}))
vi.mock('@/api/resources', () => ({ loadResourceIndex: apiResourceIndex }))

describe('dashboard expense structure', () => {
  it('renders prototype-style donut with category legend using monthly expenses', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 2, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' }
    apiOverview.mockResolvedValue({ income: 10000, expense: 200, balance: 9800 })
    apiResourceIndex.mockResolvedValue({ resources: [] })
    apiTransactions.mockImplementation(({ pageSize }: { pageSize: number }) => Promise.resolve(pageSize === 5
      ? { records: [], total: 0 }
      : { records: [
        { id: 1, type: 'EXPENSE', amount: 150, category: '居住' },
        { id: 2, type: 'EXPENSE', amount: 50, category: '餐饮' },
        { id: 3, type: 'INCOME', amount: 1000, category: '薪资' },
      ], total: 3 }))

    const tempWrapper = shallowMount(DashboardView, { global: { stubs: { 'router-link': true } } })
    await flushPromises()

    expect(tempWrapper.find('.pie-wrap').exists()).toBe(true)
    expect(tempWrapper.find('.card-head p').text()).toBe('按分类统计 · 共 ¥200.00')
    expect(tempWrapper.find('.pie svg').attributes('viewBox')).toBe('0 0 200 200')
    expect(tempWrapper.findAll('.pie svg path')).toHaveLength(2)
    expect(tempWrapper.find('.pc-value').text()).toBe('¥200.00')
    expect(tempWrapper.find('.pc-sub').text()).toBe('2 个分类')
    expect(tempWrapper.findAll('.lg-row').map((row) => row.text())).toEqual([
      '居住¥150.0075.0%',
      '餐饮¥50.0025.0%',
    ])
    tempWrapper.unmount()
  })

  it('shows a one-cent expense in the header, donut center and legend', async () => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    const tempAuthStore = useAuthStore()
    tempAuthStore.user = { id: 2, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' }
    apiOverview.mockResolvedValue({ income: 0, expense: 0.01, balance: -0.01 })
    apiResourceIndex.mockResolvedValue({ resources: [] })
    apiTransactions.mockImplementation(({ pageSize }: { pageSize: number }) => Promise.resolve(pageSize === 5
      ? { records: [], total: 0 }
      : { records: [{ id: 1, type: 'EXPENSE', amount: 0.01, category: '餐饮' }], total: 1 }))

    const tempWrapper = shallowMount(DashboardView, { global: { stubs: { 'router-link': true } } })
    await flushPromises()

    expect(tempWrapper.find('.card-head p').text()).toBe('按分类统计 · 共 ¥0.01')
    expect(tempWrapper.find('.pc-value').text()).toBe('¥0.01')
    expect(tempWrapper.find('.lg-val').text()).toBe('¥0.01')
    tempWrapper.unmount()
  })
})
