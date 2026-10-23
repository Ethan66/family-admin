import { nextTick } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PhotoDirectoryTree from '@/components/PhotoDirectoryTree.vue'
import type { PhotoDirectoryNode } from '@/utils/photoDirectory'

const DIRECTORIES: PhotoDirectoryNode[] = [{
  path: 'D:/photos', name: 'photos', count: 2, children: [{
    path: 'D:/photos/trip', name: 'trip', count: 2, children: [{
      path: 'D:/photos/trip/empty', name: 'empty', count: 0, children: [],
    }],
  }],
}]

describe('photo directory navigation', () => {
  it('places each root JSON after all visible child folders and before the next root', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: [...DIRECTORIES, { path: 'D:/other', name: 'other', count: 0, children: [] }],
      selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos', 'D:/other'], removeDisabled: false,
      jsonRootPaths: ['D:/photos', 'D:/other'],
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    await tempWrapper.find('[aria-label="展开 trip"]').trigger('click')
    await tempWrapper.find('[aria-label="展开 other"]').trigger('click')

    const tempRows = tempWrapper.findAll('.tree-list > .tree-item').map((tempItem) =>
      tempItem.find('.tree-node').exists()
        ? tempItem.find('.tree-node').attributes('data-testid')
        : tempItem.attributes('data-testid'))
    expect(tempRows).toEqual([
      'directory-D:/photos',
      'directory-D:/photos/trip',
      'directory-D:/photos/trip/empty',
      'photo-root-json-D:/photos',
      'directory-D:/other',
      'photo-root-json-D:/other',
    ])
    tempWrapper.unmount()
  })

  it('shows the root JSON as a non-interactive leaf and offers deletion only for subfolders', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, jsonRootPaths: ['D:/photos'],
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-root-json-D:/photos"]').text()).toContain('photos-setting.json')
    expect(tempWrapper.find('[data-testid="photo-root-json-D:/photos"] button').exists()).toBe(false)
    expect(tempWrapper.find('[aria-label="删除子文件夹 photos"]').exists()).toBe(false)
    await tempWrapper.find('[aria-label="删除子文件夹 trip"]').trigger('click')
    expect(tempWrapper.emitted('deleteSubfolder')?.[0]).toEqual(['D:/photos/trip'])
    expect(tempWrapper.emitted('select')).toBeUndefined()
    tempWrapper.unmount()
  })

  it('does not claim the JSON exists before it is found on disk', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, jsonRootPaths: [],
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-root-json-D:/photos"]').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('places the add-directory action beside the tree heading', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: { nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false, removablePaths: ['D:/photos'], removeDisabled: false } })
    const tempAddButton = tempWrapper.find('.tree-heading [aria-label="添加照片目录"]')

    expect(tempAddButton.exists()).toBe(true)
    await tempAddButton.trigger('click')
    expect(tempWrapper.emitted('addDirectory')).toHaveLength(1)
    await tempWrapper.setProps({ addDisabled: true })
    expect(tempAddButton.attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('expands folders without selecting them and emits a path only when its label is clicked', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: { nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false, removablePaths: ['D:/photos'], removeDisabled: false } })

    expect(tempWrapper.find('[data-testid="directory-D:/photos/trip"]').exists()).toBe(false)
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="directory-D:/photos/trip"]').exists()).toBe(true)
    expect(tempWrapper.emitted('select')).toBeUndefined()
    await tempWrapper.find('[data-testid="directory-D:/photos/trip"]').trigger('click')
    expect(tempWrapper.emitted('select')?.[0]).toEqual(['D:/photos/trip'])
    await tempWrapper.find('[aria-label="折叠 photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="directory-D:/photos/trip"]').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('only enables removing a selected explicitly added root', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false,
    } })
    const tempRemoveButton = tempWrapper.find('[aria-label="移除照片目录"]')

    expect(tempRemoveButton.exists()).toBe(true)
    expect(tempRemoveButton.attributes('disabled')).toBeDefined()
    await tempWrapper.setProps({ selectedPath: 'D:/photos/trip' })
    expect(tempRemoveButton.attributes('disabled')).toBeDefined()
    await tempWrapper.setProps({ selectedPath: 'D:/photos' })
    expect(tempRemoveButton.attributes('disabled')).toBeUndefined()
    await tempRemoveButton.trigger('click')
    expect(tempWrapper.emitted('removeDirectory')?.[0]).toEqual(['D:/photos'])
    await tempWrapper.setProps({ removeDisabled: true })
    expect(tempRemoveButton.attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('opens an inline editor from an unselected folder and creates on Enter', async () => {
    const tempCreateSubfolder = vi.fn().mockResolvedValue(true)
    const tempWrapper = mount(PhotoDirectoryTree, { attachTo: document.body, props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, createSubfolder: tempCreateSubfolder,
    } })
    expect(tempWrapper.find('.tree-heading [aria-label="新建子文件夹"]').exists()).toBe(false)
    const tempCreateButton = tempWrapper.find('[aria-label="新建子文件夹"]')
    expect(tempCreateButton.exists()).toBe(true)
    await tempCreateButton.trigger('click')
    expect(tempWrapper.emitted('select')?.[0]).toEqual(['D:/photos'])
    await tempWrapper.setProps({ selectedPath: 'D:/photos' })
    const tempInput = tempWrapper.find('[aria-label="子文件夹名称"]')
    expect(tempInput.exists()).toBe(true)
    expect(tempInput.element.parentElement?.previousElementSibling?.classList.contains('tree-row')).toBe(true)
    expect(document.activeElement).toBe(tempInput.element)
    await tempInput.setValue('新相册')
    await tempInput.trigger('keydown.enter')
    await flushPromises()
    expect(tempCreateSubfolder).toHaveBeenCalledWith('D:/photos', '新相册')
    expect(tempWrapper.find('[aria-label="子文件夹名称"]').exists()).toBe(false)
    await tempWrapper.setProps({ createDisabled: true })
    expect(tempWrapper.find('[aria-label="新建子文件夹"]').attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('opens the editor directly under a hovered child folder without selecting its parent', async () => {
    const tempCreateSubfolder = vi.fn().mockResolvedValue(true)
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: 'D:/photos', totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, createSubfolder: tempCreateSubfolder,
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    const tempChild = tempWrapper.find('[data-testid="directory-D:/photos/trip"]').element.closest('.tree-item')!
    const tempCreateButton = tempChild.querySelector<HTMLButtonElement>('[aria-label="新建子文件夹"]')!
    expect(tempCreateButton).not.toBeNull()
    tempCreateButton.click()
    await nextTick()
    expect(tempWrapper.emitted('select')?.[0]).toEqual(['D:/photos/trip'])
    const tempInput = tempWrapper.find('[aria-label="子文件夹名称"]')
    expect(tempInput.element.closest('.tree-item')).toBe(tempChild)
    await tempInput.setValue('海边')
    await tempInput.trigger('keydown.enter')
    await flushPromises()
    expect(tempCreateSubfolder).toHaveBeenCalledWith('D:/photos/trip', '海边')
    tempWrapper.unmount()
  })

  it('keeps the inline editor on creation failure and cancels it on Escape', async () => {
    const tempCreateSubfolder = vi.fn().mockResolvedValue(false)
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: 'D:/photos', totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, createSubfolder: tempCreateSubfolder,
    } })
    await tempWrapper.find('[aria-label="新建子文件夹"]').trigger('click')
    const tempInput = tempWrapper.find('[aria-label="子文件夹名称"]')
    await tempInput.trigger('keydown.enter')
    expect(tempCreateSubfolder).not.toHaveBeenCalled()
    await tempInput.setValue('重名相册')
    await tempInput.trigger('keydown.enter')
    await flushPromises()
    expect(tempWrapper.find('[aria-label="子文件夹名称"]').element).toHaveProperty('value', '重名相册')
    await tempInput.trigger('keydown.esc')
    expect(tempWrapper.find('[aria-label="子文件夹名称"]').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('accepts selected photos on added roots and child folders, highlighting either drop target', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, draggingPhotos: true,
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    const tempRootRow = tempWrapper.find('[data-testid="directory-D:/photos"]').element.parentElement!
    const tempChildRow = tempWrapper.find('[data-testid="directory-D:/photos/trip"]').element.parentElement!

    const tempRootDragOver = new Event('dragover', { bubbles: true, cancelable: true })
    tempRootRow.dispatchEvent(tempRootDragOver)
    await nextTick()
    expect(tempRootDragOver.defaultPrevented).toBe(true)
    expect(tempRootRow.classList.contains('is-drop-target')).toBe(true)
    tempRootRow.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }))
    expect(tempWrapper.emitted('dropPhotos')?.[0]).toEqual(['D:/photos'])

    const tempChildDragOver = new Event('dragover', { bubbles: true, cancelable: true })
    tempChildRow.dispatchEvent(tempChildDragOver)
    await nextTick()
    expect(tempChildDragOver.defaultPrevented).toBe(true)
    expect(tempChildRow.classList.contains('is-drop-target')).toBe(true)
    await tempWrapper.setProps({ draggingPhotos: false })
    await tempWrapper.setProps({ draggingPhotos: true })
    expect(tempChildRow.classList.contains('is-drop-target')).toBe(false)
    tempChildRow.dispatchEvent(new Event('dragover', { bubbles: true, cancelable: true }))
    tempChildRow.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }))
    expect(tempWrapper.emitted('dropPhotos')?.[1]).toEqual(['D:/photos/trip'])
    tempWrapper.unmount()
  })

  it('does not accept a drop without an active photo drag', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: {
      nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false,
      removablePaths: ['D:/photos'], removeDisabled: false, draggingPhotos: false,
    } })
    await tempWrapper.find('[aria-label="展开 photos"]').trigger('click')
    const tempChildRow = tempWrapper.find('[data-testid="directory-D:/photos/trip"]').element.parentElement!
    const tempDragOver = new Event('dragover', { bubbles: true, cancelable: true })
    tempChildRow.dispatchEvent(tempDragOver)
    tempChildRow.dispatchEvent(new Event('drop', { bubbles: true, cancelable: true }))
    expect(tempDragOver.defaultPrevented).toBe(false)
    expect(tempWrapper.emitted('dropPhotos')).toBeUndefined()
    tempWrapper.unmount()
  })
})
