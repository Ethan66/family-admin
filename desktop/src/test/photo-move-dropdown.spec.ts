import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import PhotoMoveDropdown from '@/components/PhotoMoveDropdown.vue'

const directories = [
  { path: 'D:/photos', label: '照片', isRoot: true },
  { path: 'D:/photos/trip', label: '　旅行', isRoot: false },
  { path: 'D:/photos/trip/child', label: '　　海边', isRoot: false },
  { path: 'D:/photos/family', label: '　家人', isRoot: false },
]

function mountDropdown(moveAction = vi.fn().mockResolvedValue(true)) {
  return shallowMount(PhotoMoveDropdown, {
    props: {
      variant: 'batch',
      resourceIds: ['photo-1', 'photo-2'],
      directories,
      currentDirectory: null,
      disabled: false,
      moving: false,
      moveAction,
    },
  })
}

describe('photo move dropdown', () => {
  it('filters child folders and moves to the clicked result without a dialog', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    expect(wrapper.find('.photo-move-menu').exists()).toBe(true)
    expect(wrapper.find('el-dialog-stub').exists()).toBe(false)
    await wrapper.find('[data-testid="photo-move-search"]').setValue('旅行')
    expect(wrapper.findAll('[data-testid="photo-move-option"]')).toHaveLength(1)
    await wrapper.find('[data-testid="photo-move-option"]').trigger('click')

    expect(moveAction).toHaveBeenCalledWith(['photo-1', 'photo-2'], 'D:/photos/trip', null)
    expect(wrapper.find('.photo-move-menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('uses Enter to move to the first matching folder', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').setValue('海')
    await wrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter' })

    expect(moveAction).toHaveBeenCalledWith(['photo-1', 'photo-2'], 'D:/photos/trip/child', null)
    wrapper.unmount()
  })

  it('uses Enter to create an unmatched child folder under the selected parent', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)
    await wrapper.setProps({ currentDirectory: 'D:/photos/trip' })

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').setValue('新相册')
    expect(wrapper.findAll('[data-testid="photo-move-option"]')).toHaveLength(0)
    expect(wrapper.find('[data-testid="photo-move-create"]').exists()).toBe(true)
    await wrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter' })

    expect(moveAction).toHaveBeenCalledWith(['photo-1', 'photo-2'], 'D:/photos/trip', '新相册')
    wrapper.unmount()
  })

  it('lets a user choose the creation parent when multiple directories exist', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').setValue('新相册')
    await wrapper.find('[data-testid="photo-move-parent"]').setValue('D:/photos/family')
    await wrapper.find('[data-testid="photo-move-create"]').trigger('click')

    expect(moveAction).toHaveBeenCalledWith(['photo-1', 'photo-2'], 'D:/photos/family', '新相册')
    wrapper.unmount()
  })

  it('keeps the menu open on move failure and closes it on outside click or Escape', async () => {
    const moveAction = vi.fn().mockResolvedValue(false)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-option"]').trigger('click')
    expect(wrapper.find('.photo-move-menu').exists()).toBe(true)
    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }))
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.photo-move-menu').exists()).toBe(false)
    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').trigger('keydown.esc')
    expect(wrapper.find('.photo-move-menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('closes when the selected photos change while the menu is open', async () => {
    const wrapper = mountDropdown()

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    expect(wrapper.find('.photo-move-menu').exists()).toBe(true)
    await wrapper.setProps({ resourceIds: ['photo-3', 'photo-4'] })

    expect(wrapper.find('.photo-move-menu').exists()).toBe(false)
    wrapper.unmount()
  })

  it('does not create a folder when Enter only commits Chinese input composition', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').setValue('新相册')
    await wrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter', isComposing: true })

    expect(moveAction).not.toHaveBeenCalled()
    expect(wrapper.find('.photo-move-menu').exists()).toBe(true)
    wrapper.unmount()
  })

  it('searches folder names rather than treating a matching ancestor path as a result', async () => {
    const moveAction = vi.fn().mockResolvedValue(true)
    const wrapper = mountDropdown(moveAction)

    await wrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await wrapper.find('[data-testid="photo-move-search"]').setValue('photos')

    expect(wrapper.findAll('[data-testid="photo-move-option"]')).toHaveLength(0)
    await wrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter' })
    expect(moveAction).toHaveBeenCalledWith(['photo-1', 'photo-2'], 'D:/photos', 'photos')
    wrapper.unmount()
  })
})
