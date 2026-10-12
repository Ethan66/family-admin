import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
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
  it('places the add-directory action beside the tree heading', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: { nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false } })
    const tempAddButton = tempWrapper.find('.tree-heading [aria-label="添加照片目录"]')

    expect(tempAddButton.exists()).toBe(true)
    await tempAddButton.trigger('click')
    expect(tempWrapper.emitted('addDirectory')).toHaveLength(1)
    await tempWrapper.setProps({ addDisabled: true })
    expect(tempAddButton.attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('expands folders without selecting them and emits a path only when its label is clicked', async () => {
    const tempWrapper = mount(PhotoDirectoryTree, { props: { nodes: DIRECTORIES, selectedPath: null, totalCount: 2, addDisabled: false } })

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
})
