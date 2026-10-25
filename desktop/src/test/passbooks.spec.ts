import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElDatePicker, ElSelect } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import PassbooksView from '@/views/PassbooksView.vue'

const { apiList, apiCreate, apiUpdate, apiRemove, apiMembers } = vi.hoisted(() => ({
  apiList: vi.fn(), apiCreate: vi.fn(), apiUpdate: vi.fn(), apiRemove: vi.fn(), apiMembers: vi.fn(),
}))
vi.mock('@/api/passbooks', () => ({ passbooksApi: { apiList, apiCreate, apiUpdate, apiRemove, apiMembers } }))
vi.mock('element-plus', async (importOriginal) => ({ ...await importOriginal<object>(), ElMessage: { success: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue(true) } }))

interface ViewState {
  filters: { memberId: number | ''; depositYear: string | null; sort: string; page: number }
  form: { note: string | null; bank: string | null; memberId: number | undefined; amount: number; totalInterest: number; termYears: number; interestRate: number; depositDate: string }
  formRef: { validate: () => Promise<boolean> }
  handleSearch: () => Promise<void>
  handleCreate: () => void
  handleSubmit: () => Promise<void>
  handleEdit: (record: object) => void
  recordsData: object[]
  isDialogVisible: boolean
  maturityDate: string
  totalAmount: number
  canManage: (record: object) => boolean
  handleRemove: (record: object) => Promise<void>
}
function mountView() {
  const tempWrapper = shallowMount(PassbooksView, { global: { renderStubDefaultSlot: true, stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } } } })
  const tempState = (tempWrapper.vm.$ as unknown as { setupState: ViewState }).setupState
  return { tempWrapper, tempState }
}
describe('passbooks', () => {
  beforeEach(() => {
    sessionStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
    apiList.mockResolvedValue({ records: [], total: 0, page: 1, pageSize: 20, totalPrincipal: 0, totalAmount: 0 })
    apiMembers.mockResolvedValue([{ userId: 7, displayName: '爸爸' }])
    apiCreate.mockResolvedValue({})
    apiUpdate.mockResolvedValue({})
    useAuthStore().user = { id: 7, familyId: 31, username: 'dad', displayName: '爸爸', role: 'FAMILY_MEMBER' }
  })
  it('moves page information and totals below the list and formats amounts in ten-thousand yuan units', async () => {
    apiList.mockResolvedValueOnce({ records: [{ id: 9, memberId: 7, memberName: '爸爸', bank: '农商', depositDate: '2026-02-01', amount: 50000, termYears: 3, interestRate: 1.75, maturityDate: '2029-02-01', totalInterest: 2625, totalAmount: 52625, createdBy: 7, note: null }], total: 25, page: 1, pageSize: 20, totalPrincipal: 740000, totalAmount: 773235 })
    const { tempWrapper } = mountView()
    await flushPromises()
    expect(tempWrapper.find('.card-head').exists()).toBe(false)
    const tempFooter = tempWrapper.find('.list-footer')
    expect(tempFooter.element.children[0]!.getAttribute('data-testid')).toBe('passbook-totals')
    expect(tempFooter.element.children[1]!.classList.contains('page-controls')).toBe(true)
    const tempCells = tempWrapper.findAll('tbody td')
    expect(tempCells[6]!.text()).toBe('5 万')
    expect(tempCells[7]!.text()).toBe('¥2,625.00')
    expect(tempCells[8]!.text()).toBe('5.2625 万')
    expect(tempWrapper.find('[data-testid="passbook-page-info"]').text()).toContain('第 1 / 2 页')
    expect(tempWrapper.find('[data-testid="passbook-page-info"]').text()).toContain('共 25 笔')
    expect(tempWrapper.find('[data-testid="passbook-totals"]').text()).toContain('总本金：74万')
    expect(tempWrapper.find('[data-testid="passbook-totals"]').text()).toBe('总本金：74万，总金额：77.3235万')
    tempWrapper.unmount()
  })
  it('keeps pagination visible for an empty result and resets totals when switching families', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }
    tempAuth.selectFamily(31)
    const { tempWrapper } = mountView()
    await flushPromises()
    expect(tempWrapper.find('[data-testid="passbook-page-info"]').text()).toContain('第 1 / 1 页')
    expect(tempWrapper.find('[data-testid="passbook-page-info"]').text()).toContain('共 0 笔')
    expect(tempWrapper.find('el-pagination-stub').exists()).toBe(true)
    tempAuth.selectFamily(32)
    await flushPromises()
    expect(tempWrapper.find('[data-testid="passbook-totals"]').text()).toContain('总本金：0万')
    expect(tempWrapper.find('[data-testid="passbook-totals"]').text()).toContain('总金额：0万')
    tempWrapper.unmount()
  })
  it('sorts from date column headers and restarts paging without a sort filter', async () => {
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    expect(tempWrapper.find('[data-testid="passbook-sort"]').exists()).toBe(false)
    const tempDepositButton = tempWrapper.find('[data-testid="passbook-sort-deposit"]')
    const tempMaturityButton = tempWrapper.find('[data-testid="passbook-sort-maturity"]')
    expect(tempDepositButton.exists()).toBe(true)
    expect(tempMaturityButton.exists()).toBe(true)
    expect(tempDepositButton.element.closest('th')!.getAttribute('aria-sort')).toBe('descending')
    for (const [tempButton, tempSort, tempDirection] of [
      [tempDepositButton, 'DEPOSIT_DATE_ASC', 'ascending'],
      [tempDepositButton, 'DEPOSIT_DATE_DESC', 'descending'],
      [tempMaturityButton, 'MATURITY_DATE_ASC', 'ascending'],
      [tempMaturityButton, 'MATURITY_DATE_DESC', 'descending'],
    ] as const) {
      tempState.filters.page = 3
      await tempButton.trigger('click')
      await flushPromises()
      expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ sort: tempSort, page: 1 }))
      expect(tempButton.element.closest('th')!.getAttribute('aria-sort')).toBe(tempDirection)
    }
    expect(tempDepositButton.element.closest('th')!.getAttribute('aria-sort')).toBe('none')
    tempWrapper.unmount()
  })
  it('offers the bank choice and allows clearing it without carrying it into a new form', async () => {
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    tempState.handleCreate()
    await flushPromises()
    const tempSelect = tempWrapper.findAllComponents(ElSelect).find((tempItem) => tempItem.attributes('data-testid') === 'passbook-bank')!
    expect(tempSelect).toBeDefined()
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((tempItem) => tempItem.props('value'))).toEqual(['农商'])
    expect(tempState.form.bank).toBe('农商')
    tempState.handleEdit({ id: 9, memberId: 7, bank: null, depositDate: '2026-01-01', amount: 200, termYears: 1, interestRate: 1, totalInterest: 2 })
    expect(tempState.form.bank).toBeNull()
    tempState.form.bank = ''
    tempState.formRef = { validate: async () => true }
    await tempState.handleSubmit()
    expect(apiUpdate).toHaveBeenCalledWith(9, expect.objectContaining({ bank: '' }))
    tempState.handleCreate()
    expect(tempState.form.bank).toBe('农商')
    tempWrapper.unmount()
  })
  it('filters by member and full deposit year, restarting at page one', async () => {
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    const tempYearPicker = tempWrapper.findAllComponents(ElDatePicker).find((tempItem) => tempItem.attributes('data-testid') === 'passbook-year-filter')!
    expect(tempYearPicker.props('type')).toBe('year')
    expect(tempYearPicker.props('valueFormat')).toBe('YYYY')
    Object.assign(tempState.filters, { memberId: 7, depositYear: '2026', page: 3 })
    await tempState.handleSearch()
    expect(apiList).toHaveBeenLastCalledWith({ memberId: 7, startDate: '2026-01-01', endDate: '2026-12-31', sort: 'DEPOSIT_DATE_DESC', page: 1, pageSize: 20 })
    Object.assign(tempState.filters, { memberId: '', depositYear: null })
    await tempState.handleSearch()
    expect(apiList).toHaveBeenLastCalledWith(expect.objectContaining({ memberId: undefined, startDate: undefined, endDate: undefined }))
    tempWrapper.unmount()
  })
  it('submits manual interest independently from the rate and restores edited fields', async () => {
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    tempState.handleCreate()
    await flushPromises()
    Object.assign(tempState.form, { amount: 10000, totalInterest: 123.45, termYears: 3, interestRate: 2.15, depositDate: '2024-02-29', note: '学费备用' })
    expect(tempState.maturityDate).toBe('2027-02-28')
    expect(tempState.totalAmount).toBe(10123.45)
    tempState.formRef = { validate: async () => true }
    await tempState.handleSubmit()
    expect(apiCreate).toHaveBeenCalledWith({ bank: '农商', memberId: 7, amount: 10000, totalInterest: 123.45, termYears: 3, interestRate: 2.15, depositDate: '2024-02-29', note: '学费备用' })
    tempState.handleEdit({ bank: '农商', id: 9, memberId: 7, memberName: '爸爸', amount: 200, totalInterest: 15, termYears: 2, interestRate: 3, depositDate: '2026-01-01', createdBy: 7, note: '到期续存' })
    expect(tempState.form).toMatchObject({ bank: '农商', amount: 200, totalInterest: 15, termYears: 2, note: '到期续存' })
    await flushPromises()
    tempState.formRef = { validate: async () => true }
    await tempState.handleSubmit()
    expect(apiUpdate).toHaveBeenCalledWith(9, expect.objectContaining({ amount: 200, totalInterest: 15, note: '到期续存' }))
    tempWrapper.unmount()
  })
  it('does not load before an administrator selects a family and discards stale responses', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, familyId: null, username: 'admin', displayName: '管理员', role: 'SUPER_ADMIN' }
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    expect(apiList).not.toHaveBeenCalled()
    let tempResolve!: (value: object) => void
    apiList.mockReturnValueOnce(new Promise((resolve) => { tempResolve = resolve }))
    tempAuth.selectFamily(31)
    await flushPromises()
    tempState.handleCreate()
    tempAuth.selectFamily(32)
    await flushPromises()
    tempResolve({ records: [{ id: 99 }], total: 1 })
    await flushPromises()
    expect(tempState.recordsData).toEqual([])
    expect(tempState.isDialogVisible).toBe(false)
    tempWrapper.unmount()
  })
  it('limits edit actions to creators and administrators and deletes after confirmation', async () => {
    const { tempWrapper, tempState } = mountView()
    await flushPromises()
    expect(tempState.canManage({ createdBy: 7 })).toBe(true)
    expect(tempState.canManage({ createdBy: 8 })).toBe(false)
    useAuthStore().user!.role = 'SUPER_ADMIN'
    expect(tempState.canManage({ createdBy: 8 })).toBe(true)
    await tempState.handleRemove({ id: 9, createdBy: 7 })
    expect(apiRemove).toHaveBeenCalledWith(9)
    tempWrapper.unmount()
  })
  it('renders the stored note immediately before the actions column', async () => {
    apiList.mockResolvedValueOnce({ records: [{ id: 9, memberId: 7, memberName: '爸爸', bank: '农商', depositDate: '2026-01-01', amount: 100, termYears: 1, interestRate: 1, maturityDate: '2027-01-01', totalInterest: 2, totalAmount: 102, createdBy: 7, note: '到期续存' }], total: 1, page: 1, pageSize: 20, totalPrincipal: 100, totalAmount: 102 })
    const { tempWrapper } = mountView()
    await flushPromises()
    expect(tempWrapper.findAll('thead th').at(-2)!.text()).toBe('备注')
    expect(tempWrapper.findAll('thead th').at(-1)!.text()).toBe('操作')
    expect(tempWrapper.findAll('tbody tr td').at(-2)!.text()).toBe('到期续存')
    tempWrapper.unmount()
  })
})
