import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import NoteImage from '@/components/NoteImage.vue'
import { quickNotesApi } from '@/api/quickNotes'

vi.mock('@/api/quickNotes', () => ({ quickNotesApi: { apiImage: vi.fn() } }))

afterEach(() => vi.restoreAllMocks())

describe('笔记鉴权图片', () => {
  it('ignores an old download and revokes its blob URL when unmounted', async () => {
    let tempResolve!: (blob: Blob) => void
    vi.mocked(quickNotesApi.apiImage).mockImplementationOnce(() => new Promise((resolve) => { tempResolve = resolve }))
      .mockResolvedValueOnce(new Blob(['image-b'], { type: 'image/png' }))
    Object.defineProperty(URL, 'createObjectURL', { value: vi.fn().mockReturnValue('blob:note-b'), configurable: true })
    Object.defineProperty(URL, 'revokeObjectURL', { value: vi.fn(), configurable: true })
    const tempWrapper = mount(NoteImage, { props: { fileId: 1, alt: '图片' } })
    const tempFirstSignal = vi.mocked(quickNotesApi.apiImage).mock.calls[0]![1]
    await tempWrapper.setProps({ fileId: 2 })
    await flushPromises()
    expect(tempFirstSignal.aborted).toBe(true)
    expect(tempWrapper.get('img').attributes('src')).toBe('blob:note-b')
    tempResolve(new Blob(['image-a']))
    await flushPromises()
    expect(URL.createObjectURL).toHaveBeenCalledTimes(1)
    tempWrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:note-b')
  })
})
