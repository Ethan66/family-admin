import { nextTick, reactive } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import ResourceLibraryView from '@/components/ResourceLibraryView.vue'
import PhotoDirectoryTree from '@/components/PhotoDirectoryTree.vue'
import type { ResourceIndex } from '@/types/resource'

const { loadResourceIndex, movePhotos, recyclePhoto, revealInExplorer, scanResources, readBinaryPreview, saveResourceMetadata, selectResourceDirectories } = vi.hoisted(() => ({
  loadResourceIndex: vi.fn(),
  movePhotos: vi.fn(),
  recyclePhoto: vi.fn(),
  revealInExplorer: vi.fn(),
  scanResources: vi.fn(),
  readBinaryPreview: vi.fn(),
  saveResourceMetadata: vi.fn(),
  selectResourceDirectories: vi.fn(),
}))

const TEST_AUTH_STORE = reactive({ user: { role: 'FAMILY_MEMBER', familyId: 12 }, selectedFamilyId: null })

const PHOTO_FORM_STUBS = {
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
  ElSelect: { props: ['modelValue'], template: '<button class="test-add-category" type="button" @click="$emit(\'update:modelValue\', [...modelValue, \'家人\'])">添加分类</button>' },
  ElRate: { template: '<button class="test-set-rating" type="button" @click="$emit(\'change\', 5)">五星</button>' },
}
const PHOTO_MOVE_STUBS = { PhotoMoveDropdown: false }

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => TEST_AUTH_STORE,
}))
vi.mock('@/api/resources', () => ({
  loadResourceIndex,
  movePhotos,
  recyclePhoto,
  openWithDefaultApp: vi.fn(),
  readBinaryPreview,
  readTextPreview: vi.fn(),
  revealInExplorer,
  scanResources,
  saveResourceMetadata,
  selectResourceDirectories,
}))

const PHOTO_INDEX: ResourceIndex = {
  schemaVersion: 2,
  familyId: 12,
  roots: { photos: ['D:/photos'], movies: [], notes: [], exams: [] },
  lastScannedAt: null,
  resources: [{
    id: 'photo-1',
    kind: 'PHOTO',
    path: 'D:/photos/family.jpg',
    name: 'family.jpg',
    extension: '.jpg',
    size: 1024,
    modifiedAt: '2026-10-09T12:00:00Z',
  }],
  warnings: [],
  photoDirectories: ['D:/photos'],
  metadataById: {},
}

const MULTI_PHOTO_INDEX: ResourceIndex = {
  ...PHOTO_INDEX,
  resources: [
    PHOTO_INDEX.resources[0],
    { ...PHOTO_INDEX.resources[0], id: 'photo-2', path: 'D:/photos/second.jpg', name: 'second.jpg' },
  ],
  metadataById: {
    'photo-1': { kind: 'PHOTO', details: { title: '一二三四五六七八九十甲乙丙丁戊己', album: '家人', date: '2026-10-09', description: '', categories: ['旅行'], rating: 4 } },
  },
}

const DIRECTORY_INDEX: ResourceIndex = {
  ...PHOTO_INDEX,
  photoDirectories: ['D:/photos', 'D:/photos/trip', 'D:/photos/trip/child', 'D:/photos/trip2', 'D:/photos/empty'],
  resources: [
    PHOTO_INDEX.resources[0],
    { ...PHOTO_INDEX.resources[0], id: 'photo-2', path: 'D:/photos/trip/second.jpg', name: 'second.jpg' },
    { ...PHOTO_INDEX.resources[0], id: 'photo-3', path: 'D:/photos/trip/child/third.jpg', name: 'third.jpg' },
    { ...PHOTO_INDEX.resources[0], id: 'photo-4', path: 'D:/photos/trip2/fourth.jpg', name: 'fourth.jpg' },
  ],
}

const PHOTO_TREE_STUB = {
  props: ['nodes', 'selectedPath'],
  template: '<div class="directory-stub" :data-selected="selectedPath ?? \'all\'"><button data-testid="choose-trip" @click="$emit(\'select\', \'D:/photos/trip\')">trip</button><button data-testid="choose-trip2" @click="$emit(\'select\', \'D:/photos/trip2\')">trip2</button><button data-testid="choose-all" @click="$emit(\'select\', null)">全部照片</button></div>',
}

describe('photo card actions', () => {
  beforeEach(() => {
    TEST_AUTH_STORE.user.familyId = 12
    readBinaryPreview.mockResolvedValue({ mimeType: 'image/jpeg', base64: 'ZmFrZQ==' })
  })
  afterEach(() => { vi.useRealTimers() })
  it('shows only delete on image hover and recycles immediately without a confirmation dialog', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    recyclePhoto.mockResolvedValue({ ...PHOTO_INDEX, resources: [] })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await nextTick()
    await nextTick()

    expect(tempWrapper.find('.photo-actions').exists()).toBe(true)
    expect(tempWrapper.findAll('.photo-actions button')).toHaveLength(1)
    expect(tempWrapper.find('.photo-actions button').attributes('title')).toBe('删除照片')
    expect(tempWrapper.find('.photo-info .btn-text').exists()).toBe(false)
    expect(tempWrapper.find('.photo-title').text()).toContain('family')
    expect(tempWrapper.find('.photo-sub').text()).toBe('2026-10-09 · 1 KB')

    await tempWrapper.find('button[title="删除照片"]').trigger('click')
    await nextTick()

    expect(recyclePhoto).toHaveBeenCalledWith(12, 'photo-1')
    expect(tempWrapper.find('.photo-card').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('locates the selected image from the right file information', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    revealInExplorer.mockResolvedValue(undefined)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    const tempLocateButton = tempWrapper.find('[data-testid="locate-selected-photo"]')
    expect(tempLocateButton.exists()).toBe(true)
    await tempLocateButton.trigger('click')

    expect(revealInExplorer).toHaveBeenCalledWith(12, 'D:/photos/family.jpg')
    tempWrapper.unmount()
  })

  it('uses Ctrl-click to deselect a photo without a checkbox and keeps long titles truncated', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()

    const tempFirstCard = tempWrapper.findAll('.photo-card')[0]
    expect(tempFirstCard.find('.photo-media input[type="checkbox"]').exists()).toBe(false)
    expect(tempFirstCard.find('.photo-media .photo-badge').exists()).toBe(false)
    expect(tempFirstCard.find('.photo-title').text()).toContain('一二三四五六七八九十甲乙丙丁戊...')
    expect(tempFirstCard.find('.photo-title').attributes('title')).toBe('一二三四五六七八九十甲乙丙丁戊己')
    vi.useFakeTimers()
    await tempFirstCard.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    expect(tempFirstCard.classes()).toContain('is-selected')
    expect(tempWrapper.find('.photo-inspector-preview').exists()).toBe(true)
    await tempFirstCard.find('.photo-open').trigger('click', { ctrlKey: true })
    await vi.advanceTimersByTimeAsync(260)
    expect(tempFirstCard.classes()).not.toContain('is-selected')
    tempWrapper.unmount()
  })

  it('opens a large preview on double click without toggling selection', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    expect(readBinaryPreview).not.toHaveBeenCalled()
    await tempWrapper.find('.photo-open').trigger('dblclick')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.find('.photo-card').classes()).not.toContain('is-selected')
    expect(readBinaryPreview).toHaveBeenCalledWith(12, 'D:/photos/family.jpg')
    tempWrapper.unmount()
  })

  it('replaces the selected photo when another photo is clicked normally', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    const tempPhotoButtons = tempWrapper.findAll('.photo-open')
    await tempPhotoButtons[0].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempPhotoButtons[1].trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[1].classes()).toContain('is-selected')
    tempWrapper.unmount()
  })

  it('keeps the same photo selected on a second ordinary click', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.find('.photo-card').classes()).toContain('is-selected')
    tempWrapper.unmount()
  })

  it('keeps Ctrl-clicked photos selected for batch actions and toggles them off', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    const tempPhotoButtons = tempWrapper.findAll('.photo-open')
    await tempPhotoButtons[0].trigger('click')
    await tempPhotoButtons[1].trigger('click', { ctrlKey: true })
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(2)
    await tempPhotoButtons[0].trigger('click', { ctrlKey: true })
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[1].classes()).toContain('is-selected')
    tempWrapper.unmount()
  })

  it('shows the remaining selected photo after deleting the inspected photo', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    recyclePhoto.mockResolvedValue({ ...MULTI_PHOTO_INDEX, resources: [MULTI_PHOTO_INDEX.resources[0]] })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.findAll('.photo-open')[0].trigger('click')
    await tempWrapper.findAll('.photo-open')[1].trigger('click', { ctrlKey: true })
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.findAll('button[title="删除照片"]')[1].trigger('click')
    await nextTick()

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.find('.photo-file-info').text()).toContain('family.jpg')
    tempWrapper.unmount()
  })

  it('saves categories and rating from the right inspector to family metadata', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    saveResourceMetadata.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.find('.photo-inspector').text()).toContain('分类')
    expect(tempWrapper.find('.photo-inspector').text()).toContain('评分')
    await tempWrapper.find('[data-testid="save-photo-metadata"]').trigger('click')
    await nextTick()

    expect(saveResourceMetadata).toHaveBeenCalledWith(12, 'photo-1', {
      kind: 'PHOTO', details: {
        title: '一二三四五六七八九十甲乙丙丁戊己', album: '家人', date: '2026-10-09',
        description: '', categories: ['旅行'], rating: 4,
      },
    })
    tempWrapper.unmount()
  })

  it('shows the selected photo parent folder and current categories beside their controls', async () => {
    loadResourceIndex.mockResolvedValue({
      ...MULTI_PHOTO_INDEX,
      photoDirectories: ['D:/photos', 'D:/photos/trip'],
      resources: [
        MULTI_PHOTO_INDEX.resources[0],
        { ...MULTI_PHOTO_INDEX.resources[1], path: 'D:/photos/trip/second.jpg' },
      ],
      metadataById: {
        ...MULTI_PHOTO_INDEX.metadataById,
        'photo-2': { kind: 'PHOTO', details: { title: 'second', album: '', date: '', description: '', categories: ['节日'], rating: null } },
      },
    })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.findAll('.photo-open')[0].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.find('.photo-current-parent').text()).toBe('photos')
    expect(tempWrapper.find('.photo-current-categories').text()).toBe('旅行')

    await tempWrapper.findAll('.photo-open')[1].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.find('.photo-current-parent').text()).toBe('trip')
    expect(tempWrapper.find('.photo-current-categories').text()).toBe('节日')
    tempWrapper.unmount()
  })

  it('adds a category and changes the star rating before saving', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    saveResourceMetadata.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.find('.test-add-category').trigger('click')
    await tempWrapper.find('.test-set-rating').trigger('click')
    await tempWrapper.find('[data-testid="save-photo-metadata"]').trigger('click')
    await nextTick()

    expect(saveResourceMetadata).toHaveBeenCalledWith(12, 'photo-1', expect.objectContaining({
      kind: 'PHOTO', details: expect.objectContaining({ categories: ['旅行', '家人'], rating: 5 }),
    }))
    tempWrapper.unmount()
  })

  it('uses the directory tree instead of JSON album filter chips', async () => {
    loadResourceIndex.mockResolvedValue({
      ...PHOTO_INDEX,
      metadataById: {
        'photo-1': { kind: 'PHOTO', details: { title: 'family', album: '毕业旅行', date: '', description: '', categories: [], rating: null } },
        'removed-photo': { kind: 'PHOTO', details: { title: 'old', album: '旧相册', date: '', description: '', categories: [], rating: null } },
      },
    })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()

    expect(tempWrapper.find('.photo-card-head').exists()).toBe(false)
    expect(tempWrapper.find('.photo-album-tag').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('shows four icon-only batch actions with accessible changing labels', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await nextTick()
    await nextTick()

    expect(tempWrapper.find('.photo-batch-actions').exists()).toBe(true)
    expect(tempWrapper.find('.photo-more-dropdown').exists()).toBe(false)
    expect(tempWrapper.findAll('.photo-tools > button[aria-label]')).toHaveLength(2)
    expect(tempWrapper.find('.photo-tools [aria-label="添加照片目录"]').exists()).toBe(false)
    expect(tempWrapper.find('.photo-tools .photo-search-input').exists()).toBe(false)
    await tempWrapper.find('[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('.photo-tools .photo-search-input').exists()).toBe(true)
    expect(tempWrapper.findAll('.photo-batch-actions button')).toHaveLength(4)
    expect(tempWrapper.find('.photo-batch-actions').text()).toBe('')
    expect(tempWrapper.find('[data-testid="select-all-photos"]').attributes('aria-label')).toBe('全选当前结果')
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('title')).toBe('取消选择（0张）')
    expect(tempWrapper.find('[data-testid="batch-delete-photos"]').attributes('aria-label')).toBe('批量删除')
    expect(tempWrapper.find('[data-testid="batch-move-photos"]').attributes('aria-label')).toBe('移动所选照片')
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('disabled')).toBeDefined()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="select-all-photos"]').attributes('aria-label')).toBe('取消全选')
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('aria-label')).toBe('取消选择（2张）')
    await tempWrapper.find('[data-testid="clear-photo-selection"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('aria-label')).toBe('取消选择（0张）')
    tempWrapper.unmount()
  })

  it('uses the left tree add-directory action to open the existing directory picker', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    selectResourceDirectories.mockResolvedValue([])
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()

    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('addDirectory')
    await flushPromises()

    expect(selectResourceDirectories).toHaveBeenCalledOnce()
    tempWrapper.unmount()
  })

  it('moves all selected photos into an existing child folder', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    const movedIndex = { ...DIRECTORY_INDEX, resources: DIRECTORY_INDEX.resources.map((resource) => ({
      ...resource, path: `D:/photos/trip/${resource.name}`,
    })) }
    movePhotos.mockResolvedValue(movedIndex)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await nextTick()
    await nextTick()

    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    expect(tempWrapper.find('.photo-move-menu').exists()).toBe(true)
    expect(tempWrapper.find('.photo-move-dialog').exists()).toBe(false)
    await tempWrapper.find('[data-testid="photo-move-search"]').setValue('trip')
    await tempWrapper.find('[data-testid="photo-move-option"]').trigger('click')
    await nextTick()

    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-1', 'photo-2', 'photo-3', 'photo-4'], 'D:/photos/trip', null)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.find('.photo-move-menu').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('moves one inspected photo to a newly named child folder', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    movePhotos.mockResolvedValue({ ...PHOTO_INDEX, resources: [{ ...PHOTO_INDEX.resources[0], id: 'moved-photo', path: 'D:/photos/旅行/family.jpg' }], photoDirectories: ['D:/photos', 'D:/photos/旅行'] })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: { ...PHOTO_FORM_STUBS, ...PHOTO_MOVE_STUBS } } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.find('[data-testid="move-single-photo"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-move-search"]').setValue('旅行')
    await tempWrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter' })
    await nextTick()

    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-1'], 'D:/photos', '旅行')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    tempWrapper.unmount()
  })

  it('keeps the selection and move menu open when moving fails', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    movePhotos.mockRejectedValue(new Error('目标文件夹已存在同名照片'))
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await nextTick()
    await nextTick()

    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-move-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-move-search"]').setValue('旅行')
    await tempWrapper.find('[data-testid="photo-move-search"]').trigger('keydown', { key: 'Enter' })
    await nextTick()

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.find('.photo-move-menu').exists()).toBe(true)
    tempWrapper.unmount()
  })

  it('selects all visible photos and recycles each selected file', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    let tempRemainingResources = [...MULTI_PHOTO_INDEX.resources]
    recyclePhoto.mockImplementation(async (_familyId: number, resourceId: string) => {
      tempRemainingResources = tempRemainingResources.filter((resource) => resource.id !== resourceId)
      return { ...MULTI_PHOTO_INDEX, resources: tempRemainingResources }
    })
    const tempConfirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(undefined as never)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()

    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(2)
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('aria-label')).toBe('取消选择（2张）')
    await tempWrapper.find('[data-testid="batch-delete-photos"]').trigger('click')
    await nextTick()
    await nextTick()

    expect(tempConfirm).toHaveBeenCalled()
    expect(recyclePhoto).toHaveBeenCalledWith(12, 'photo-1')
    expect(recyclePhoto).toHaveBeenCalledWith(12, 'photo-2')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(0)
    tempConfirm.mockRestore()
    tempWrapper.unmount()
  })

  it('filters a selected directory recursively and clears hidden photo selections', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await nextTick()
    await nextTick()

    expect(tempWrapper.findAll('.photo-card')).toHaveLength(4)
    vi.useFakeTimers()
    await tempWrapper.findAll('.photo-open')[0].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(2)
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('D:/photos/trip')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    await tempWrapper.find('[data-testid="choose-all"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(4)
    tempWrapper.unmount()
  })

  it('navigates folder history and drops forward history after a new tree selection', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await nextTick()
    await nextTick()

    expect(tempWrapper.find('.photo-current-directory').text()).toBe('全部照片')
    expect(tempWrapper.find('[aria-label="返回上一个文件夹"]').attributes('disabled')).toBeDefined()
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="choose-trip2"]').trigger('click')
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('trip2')
    await tempWrapper.find('[aria-label="返回上一个文件夹"]').trigger('click')
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('trip')
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('D:/photos/trip')
    await tempWrapper.find('[aria-label="前进到下一个文件夹"]').trigger('click')
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('trip2')
    await tempWrapper.find('[aria-label="返回上一个文件夹"]').trigger('click')
    await tempWrapper.find('[data-testid="choose-all"]').trigger('click')
    expect(tempWrapper.find('[aria-label="前进到下一个文件夹"]').attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('resizes gallery thumbnails with the slider and plus/minus limits', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()

    expect(tempWrapper.find('.photo-library-main').attributes('style')).toContain('--photo-thumbnail-size: 140px')
    await tempWrapper.find('[aria-label="放大照片缩略图"]').trigger('click')
    expect(tempWrapper.find('.photo-library-main').attributes('style')).toContain('--photo-thumbnail-size: 160px')
    await tempWrapper.find('[aria-label="缩小照片缩略图"]').trigger('click')
    expect(tempWrapper.find('.photo-library-main').attributes('style')).toContain('--photo-thumbnail-size: 140px')
    await tempWrapper.find('[aria-label="调整照片缩略图大小"]').setValue('240')
    expect(tempWrapper.find('.photo-library-main').attributes('style')).toContain('--photo-thumbnail-size: 240px')
    expect(tempWrapper.find('[aria-label="放大照片缩略图"]').attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('returns to all photos when a rescan removes the selected directory', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    scanResources.mockResolvedValue({
      ...DIRECTORY_INDEX,
      photoDirectories: ['D:/photos', 'D:/photos/trip2'],
      resources: DIRECTORY_INDEX.resources.filter((resource) => !resource.path.includes('/trip/')),
    })
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB, ElButton: { template: '<button><slot /></button>' } } },
    })
    await nextTick()
    await nextTick()

    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(2)
    const tempRescanButton = tempWrapper.find('[aria-label="重新扫描"]')
    await tempRescanButton?.trigger('click')
    await nextTick()
    await nextTick()
    expect(scanResources).toHaveBeenCalledWith(12, 'PHOTO', ['D:/photos'])
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(2)
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('全部照片')
    expect(tempWrapper.find('[aria-label="返回上一个文件夹"]').attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })

  it('clears the selected directory when switching families', async () => {
    loadResourceIndex.mockImplementation(async (familyId: number) => familyId === 12 ? DIRECTORY_INDEX : {
      ...DIRECTORY_INDEX, familyId, roots: { ...DIRECTORY_INDEX.roots, photos: ['E:/other'] },
      photoDirectories: ['E:/other'], resources: [],
    })
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await nextTick()
    await nextTick()

    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    TEST_AUTH_STORE.user.familyId = 13
    await nextTick()
    await nextTick()
    expect(loadResourceIndex).toHaveBeenCalledWith(13)
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('all')
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('全部照片')
    expect(tempWrapper.find('[aria-label="返回上一个文件夹"]').attributes('disabled')).toBeDefined()
    tempWrapper.unmount()
  })
})
