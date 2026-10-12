import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api/modules', () => ({
  notesApi: { list: vi.fn().mockResolvedValue({ records: [], total: 0, page: 1, pageSize: 20 }), remove: vi.fn() },
}))

import NotesView from '@/views/NotesView.vue'

describe('NotesView', () => {
  it('is read-only except for preview and delete', () => {
    const wrapper = mount(NotesView, { global: { stubs: { ElEmpty: true, ElPagination: true } } })
    expect(wrapper.text()).not.toContain('新增笔记')
    expect(wrapper.text()).not.toContain('编辑笔记')
    expect(wrapper.text()).toContain('学习笔记')
  })
})
