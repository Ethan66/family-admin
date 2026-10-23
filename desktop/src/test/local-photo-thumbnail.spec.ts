import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import LocalPhotoThumbnail from '@/components/LocalPhotoThumbnail.vue'
import type { PreviewData, ResourceEntry } from '@/types/resource'

const { readBinaryPreview } = vi.hoisted(() => ({ readBinaryPreview: vi.fn() }))

vi.mock('@/api/resources', () => ({ readBinaryPreview }))

const PHOTO_A: ResourceEntry = {
  id: 'photo-a', kind: 'PHOTO', path: 'D:/photos/a.jpg', name: 'a.jpg', extension: '.jpg', size: 1024, modifiedAt: '2026-10-12T10:00:00Z',
}
const PHOTO_B: ResourceEntry = {
  id: 'photo-b', kind: 'PHOTO', path: 'D:/photos/b.jpg', name: 'b.jpg', extension: '.jpg', size: 1024, modifiedAt: '2026-10-12T10:00:00Z',
}

let intersectionCallback: IntersectionObserverCallback

describe('LocalPhotoThumbnail', () => {
  beforeEach(() => {
    readBinaryPreview.mockReset()
    const tempCanvasSources = new WeakMap<HTMLCanvasElement, string>()
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) { intersectionCallback = callback }
      observe(): void {}
      disconnect(): void {}
    })
    vi.stubGlobal('Image', class {
      width = 640
      height = 480
      src = ''
      async decode(): Promise<void> {}
    })
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
      return { drawImage: (tempImage: HTMLImageElement) => tempCanvasSources.set(this, tempImage.src) } as unknown as CanvasRenderingContext2D
    })
    vi.spyOn(HTMLCanvasElement.prototype, 'toDataURL').mockImplementation(function (this: HTMLCanvasElement) {
      return tempCanvasSources.get(this) ?? ''
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('loads the next photo when the visible thumbnail receives a new resource', async () => {
    readBinaryPreview.mockResolvedValueOnce({ mimeType: 'image/jpeg', base64: 'QUFB' })
      .mockResolvedValueOnce({ mimeType: 'image/jpeg', base64: 'QkJC' })
    const tempWrapper = mount(LocalPhotoThumbnail, { props: { familyId: 12, resource: PHOTO_A } })

    expect(readBinaryPreview).not.toHaveBeenCalled()
    intersectionCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    await flushPromises()
    expect(tempWrapper.find('img').attributes('src')).toBe('data:image/jpeg;base64,QUFB')

    await tempWrapper.setProps({ resource: PHOTO_B })
    await flushPromises()
    expect(readBinaryPreview).toHaveBeenCalledWith(12, 'D:/photos/b.jpg')
    expect(tempWrapper.find('img').attributes('src')).toBe('data:image/jpeg;base64,QkJC')
    tempWrapper.unmount()
  })

  it('keeps the existing thumbnail when only the resource object is refreshed', async () => {
    readBinaryPreview.mockResolvedValue({ mimeType: 'image/jpeg', base64: 'QUFB' })
    const tempWrapper = mount(LocalPhotoThumbnail, { props: { familyId: 12, resource: PHOTO_A } })

    intersectionCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    await flushPromises()
    await tempWrapper.setProps({ resource: { ...PHOTO_A } })
    await flushPromises()

    expect(tempWrapper.find('img').attributes('src')).toBe('data:image/jpeg;base64,QUFB')
    expect(readBinaryPreview).toHaveBeenCalledTimes(1)
    tempWrapper.unmount()
  })

  it('ignores an earlier photo response after switching to a newer photo', async () => {
    let resolveFirstPreview!: (value: PreviewData) => void
    readBinaryPreview.mockImplementationOnce(() => new Promise<PreviewData>((resolve) => { resolveFirstPreview = resolve }))
      .mockResolvedValueOnce({ mimeType: 'image/jpeg', base64: 'QkJC' })
    const tempWrapper = mount(LocalPhotoThumbnail, { props: { familyId: 12, resource: PHOTO_A } })

    intersectionCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    await tempWrapper.setProps({ resource: PHOTO_B })
    await flushPromises()
    expect(tempWrapper.find('img').attributes('src')).toBe('data:image/jpeg;base64,QkJC')

    resolveFirstPreview({ mimeType: 'image/jpeg', base64: 'QUFB' })
    await flushPromises()
    expect(tempWrapper.find('img').attributes('src')).toBe('data:image/jpeg;base64,QkJC')
    tempWrapper.unmount()
  })
})
