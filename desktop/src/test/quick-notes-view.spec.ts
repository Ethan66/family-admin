import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import { quickNotesApi } from '@/api/quickNotes'
import QuickNotesView from '@/views/QuickNotesView.vue'
import type { QuickNote } from '@/types/quickNote'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/quickNotes', () => ({ quickNotesApi: { apiList: vi.fn(), apiCreate: vi.fn(), apiUpdate: vi.fn(), apiRemove: vi.fn(), apiUpload: vi.fn(), apiImage: vi.fn() } }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') } }))

const note: QuickNote = {
  id: 1, title: '周末去露营', body: '准备野餐垫', tags: ['旅行'], imageIds: [], tasks: [{ text: '相机', done: false }],
  color: 'plain', pinned: false, status: 'ACTIVE', version: 0, createdBy: 7,
  createdAt: '2026-10-27T09:20:00', updatedAt: '2026-10-27T09:20:00',
}

describe('随身记', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    vi.mocked(ElMessageBox.confirm).mockResolvedValue('confirm' as never)
    sessionStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().user = { id: 7, username: 'test', familyId: 31, displayName: '家人', role: 'FAMILY_MEMBER' }
    vi.mocked(quickNotesApi.apiList).mockResolvedValue([structuredClone(note)])
    vi.mocked(quickNotesApi.apiCreate).mockResolvedValue({ ...note, id: 2, body: '今天的小想法', tasks: [] })
    vi.mocked(quickNotesApi.apiUpdate).mockImplementation(async (id, body) => ({ ...note, ...body, id, version: body.version + 1 }))
  })

  it('opens a blank editor from the new button and blocks duplicate submissions', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    expect(tempWrapper.find('.quick-composer').exists()).toBe(false)
    expect(tempWrapper.find('.view-switch').exists()).toBe(false)
    expect(tempWrapper.get('.notes-header .new-note-button').text()).toBe('新建')
    expect(tempWrapper.find('.note-editor').exists()).toBe(true)
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    await tempWrapper.get('.new-note-button').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    await tempWrapper.get('.editor-body').setValue('今天的小想法')
    await Promise.all([tempWrapper.get('.editor-save').trigger('click'), tempWrapper.get('.editor-save').trigger('click')])
    await flushPromises()
    expect(quickNotesApi.apiCreate).toHaveBeenCalledTimes(1)
    expect(quickNotesApi.apiCreate).toHaveBeenCalledWith(expect.objectContaining({ body: '今天的小想法', status: 'ACTIVE', version: 0 }))
    expect(tempWrapper.find('.note-editor').exists()).toBe(true)
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('')
    await tempWrapper.get('.new-note-button').trigger('click')
    await flushPromises()
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('')
    tempWrapper.unmount()
  })

  it('filters search and tags, and canceling an edit leaves persisted content untouched', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    await tempWrapper.get('.search-input').setValue('不存在')
    expect(tempWrapper.findAll('.note-card')).toHaveLength(0)
    await tempWrapper.get('.search-input').setValue('旅行')
    expect(tempWrapper.findAll('.note-card')).toHaveLength(1)
    await tempWrapper.get('.note-open').trigger('click')
    await tempWrapper.get('.editor-body').setValue('未保存的修改')
    await tempWrapper.get('.editor-cancel').trigger('click')
    await flushPromises()
    expect(quickNotesApi.apiUpdate).not.toHaveBeenCalled()
    expect(tempWrapper.get('.note-card').text()).toContain('准备野餐垫')
    expect(tempWrapper.find('.note-editor').exists()).toBe(true)
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('')
    tempWrapper.unmount()
  })

  it('saves edits and toggles a checklist item using its version', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    await tempWrapper.get('.card-task input').setValue(true)
    await flushPromises()
    expect(quickNotesApi.apiUpdate).toHaveBeenCalledWith(1, expect.objectContaining({ version: 0, tasks: [{ text: '相机', done: true }] }))
    await tempWrapper.get('.note-open').trigger('click')
    await tempWrapper.get('.editor-body').setValue('更新的内容')
    await tempWrapper.get('.editor-save').trigger('click')
    await flushPromises()
    expect(quickNotesApi.apiUpdate).toHaveBeenLastCalledWith(1, expect.objectContaining({ version: 1, body: '更新的内容' }))
    tempWrapper.unmount()
  })

  it('does not offer edit actions for another author', async () => {
    vi.mocked(quickNotesApi.apiList).mockResolvedValue([{ ...note, createdBy: 8 }])
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    expect((tempWrapper.get('.card-task input').element as HTMLInputElement).disabled).toBe(true)
    await tempWrapper.get('.note-open').trigger('click')
    expect(tempWrapper.find('.editor-save').exists()).toBe(false)
    expect(tempWrapper.text()).toContain('只读')
    tempWrapper.unmount()
  })

  it('keeps the editor draft when discarding changes is canceled', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    await tempWrapper.get('.note-open').trigger('click')
    await tempWrapper.get('.editor-body').setValue('还想继续编辑')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await tempWrapper.get('.editor-cancel').trigger('click')
    await flushPromises()
    expect(tempWrapper.find('.note-editor').exists()).toBe(true)
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('还想继续编辑')
    expect(quickNotesApi.apiUpdate).not.toHaveBeenCalled()
    tempWrapper.unmount()
  })

  it('resets the selected note without hiding the panel when closing it', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    await tempWrapper.get('.note-open').trigger('click')
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('准备野餐垫')
    await tempWrapper.get('.editor-heading-actions button[aria-label="清空当前笔记"]').trigger('click')
    await flushPromises()
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    expect((tempWrapper.get('.editor-title').element as HTMLInputElement).value).toBe('')
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('')
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    tempWrapper.unmount()
  })

  it('pins, archives and directly deletes a note without a recycle bin', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    expect(tempWrapper.text()).not.toContain('回收站')
    const tempAction = async (label: string) => {
      await tempWrapper.findAll('.menu-content button').find((button) => button.text() === label)!.trigger('click')
      await flushPromises()
    }
    await tempAction('置顶')
    expect(quickNotesApi.apiUpdate).toHaveBeenLastCalledWith(1, expect.objectContaining({ pinned: true, version: 0 }))
    await tempAction('归档')
    expect(tempWrapper.findAll('.note-card')).toHaveLength(0)
    await tempWrapper.findAll('.note-categories button').find((button) => button.text().includes('归档'))!.trigger('click')
    await tempAction('取消归档')
    await tempWrapper.findAll('.note-categories button').find((button) => button.text().includes('全部笔记'))!.trigger('click')
    await tempWrapper.get('.note-open').trigger('click')
    await tempAction('删除')
    expect(quickNotesApi.apiRemove).toHaveBeenCalledWith(1, 3)
    expect(tempWrapper.findAll('.note-card')).toHaveLength(0)
    expect(tempWrapper.get('#note-editor-heading').text()).toBe('新建笔记')
    expect((tempWrapper.get('.editor-body').element as HTMLTextAreaElement).value).toBe('')
    tempWrapper.unmount()
  })

  it('does not delete the note when confirmation is canceled', async () => {
    const tempWrapper = mount(QuickNotesView)
    await flushPromises()
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await tempWrapper.findAll('.menu-content button').find((button) => button.text() === '删除')!.trigger('click')
    await flushPromises()
    expect(quickNotesApi.apiRemove).not.toHaveBeenCalled()
    expect(tempWrapper.findAll('.note-card')).toHaveLength(1)
    tempWrapper.unmount()
  })

  it('clears the editor and ignores an older request when changing families', async () => {
    const tempAuth = useAuthStore()
    tempAuth.user = { id: 1, username: 'admin', familyId: null, displayName: '管理员', role: 'SUPER_ADMIN' }
    tempAuth.selectFamily(31)
    let tempResolve!: (notes: QuickNote[]) => void
    vi.mocked(quickNotesApi.apiList).mockImplementationOnce(() => new Promise((resolve) => { tempResolve = resolve }))
    const tempWrapper = mount(QuickNotesView)
    tempAuth.selectFamily(null)
    await flushPromises()
    tempResolve([note])
    await flushPromises()
    expect(tempWrapper.text()).toContain('请先选择家庭')
    expect(tempWrapper.findAll('.note-card')).toHaveLength(0)
    expect(tempWrapper.find('.note-editor').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('disables the new button before the initial list finishes loading', async () => {
    let tempResolve!: (notes: QuickNote[]) => void
    vi.mocked(quickNotesApi.apiList).mockImplementationOnce(() => new Promise((resolve) => { tempResolve = resolve }))
    const tempWrapper = mount(QuickNotesView)
    expect((tempWrapper.get('.new-note-button').element as HTMLButtonElement).disabled).toBe(true)
    expect(quickNotesApi.apiCreate).not.toHaveBeenCalled()
    tempResolve([note])
    await flushPromises()
    expect((tempWrapper.get('.new-note-button').element as HTMLButtonElement).disabled).toBe(false)
    tempWrapper.unmount()
  })
})
