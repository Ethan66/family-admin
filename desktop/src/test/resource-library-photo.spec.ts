import { nextTick, reactive } from 'vue'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'
import ResourceLibraryView from '@/components/ResourceLibraryView.vue'
import PhotoDirectoryTree from '@/components/PhotoDirectoryTree.vue'
import type { ResourceIndex } from '@/types/resource'

const { loadResourceIndex, listPhotoRootJsonFiles, createPhotoSubfolder, movePhotos, recyclePhoto, recyclePhotoDirectory, revealInExplorer, scanResources, readBinaryPreview, saveResourceMetadata, selectResourceDirectories } = vi.hoisted(() => ({
  loadResourceIndex: vi.fn(),
  listPhotoRootJsonFiles: vi.fn().mockResolvedValue(['D:/photos']),
  createPhotoSubfolder: vi.fn(),
  movePhotos: vi.fn(),
  recyclePhoto: vi.fn(),
  recyclePhotoDirectory: vi.fn(),
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
  listPhotoRootJsonFiles,
  createPhotoSubfolder,
  movePhotos,
  recyclePhoto,
  recyclePhotoDirectory,
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
  props: ['nodes', 'selectedPath', 'createSubfolder'],
  template: '<div class="directory-stub" :data-selected="selectedPath ?? \'all\'"><button data-testid="choose-root" @click="$emit(\'select\', \'D:/photos\')">photos</button><button data-testid="choose-trip" @click="$emit(\'select\', \'D:/photos/trip\')">trip</button><button data-testid="choose-child" @click="$emit(\'select\', \'D:/photos/trip/child\')">child</button><button data-testid="choose-trip2" @click="$emit(\'select\', \'D:/photos/trip2\')">trip2</button><button data-testid="choose-all" @click="$emit(\'select\', null)">全部照片</button><button data-testid="create-subfolder" @click="createSubfolder(selectedPath, \'新相册\')">新建子文件夹</button><button data-testid="remove-directory" @click="$emit(\'removeDirectory\', selectedPath)">移除</button><button data-testid="delete-subfolder" @click="$emit(\'deleteSubfolder\', \'D:/photos/trip\')">删除子文件夹</button></div>',
}

describe('photo card actions', () => {
  it('groups and sorts photos by capture time with modified time fallback', async () => {
    const tempResources = [
      { ...PHOTO_INDEX.resources[0], id: 'late', path: 'D:/photos/a.jpg', name: 'a.jpg', capturedAt: '2026-09-30T23:59:59', modifiedAt: '2026-10-20T00:00:00Z' },
      { ...PHOTO_INDEX.resources[0], id: 'early', path: 'D:/photos/b.jpg', name: 'b.jpg', capturedAt: '2026-10-02T08:00:00', modifiedAt: '2026-10-21T00:00:00Z' },
      { ...PHOTO_INDEX.resources[0], id: 'latest', path: 'D:/photos/c.jpg', name: 'c.jpg', capturedAt: '2026-10-02T18:00:00', modifiedAt: '2026-10-01T00:00:00Z' },
      { ...PHOTO_INDEX.resources[0], id: 'fallback', path: 'D:/photos/d.jpg', name: 'd.jpg', modifiedAt: '2026-10-03T12:00:00Z' },
    ]
    loadResourceIndex.mockResolvedValue({ ...PHOTO_INDEX, resources: tempResources, metadataById: {
      early: { kind: 'PHOTO', details: { title: 'early', album: '', date: '2026-08-01', description: '', categories: [], rating: null } },
    } })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()

    expect(tempWrapper.findAll('.group-label').map((group) => group.text())).toEqual(['2026-10 · 3 张', '2026-09 · 1 张'])
    expect(tempWrapper.findAll('.photo-card').map((card) => card.find('.photo-title').text())).toEqual(['d', 'c', 'early', 'a'])
    tempWrapper.unmount()
  })

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

  it('keeps the inspector actions in a bottom dock without its heading or parent folder row', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_FORM_STUBS } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.find('.photo-inspector h3').exists()).toBe(false)
    expect(tempWrapper.find('.photo-current-parent').exists()).toBe(false)
    expect(tempWrapper.find('.photo-inspector-scroll .photo-inspector-form').exists()).toBe(true)
    expect(tempWrapper.find('.photo-inspector-actions [data-testid="locate-selected-photo"]').exists()).toBe(true)
    expect(tempWrapper.find('.photo-inspector-actions [data-testid="save-photo-metadata"]').exists()).toBe(true)
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

  it('deselects the same photo on a second ordinary click', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempWrapper.find('.photo-open').trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.find('.photo-card').classes()).not.toContain('is-selected')
    expect(tempWrapper.find('.photo-inspector-preview').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('deselects only the clicked photo from a multi-selection without Ctrl', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await nextTick()
    await nextTick()
    vi.useFakeTimers()

    const tempPhotoButtons = tempWrapper.findAll('.photo-open')
    await tempPhotoButtons[0].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    await tempPhotoButtons[1].trigger('click', { ctrlKey: true })
    await vi.advanceTimersByTimeAsync(260)
    await tempPhotoButtons[1].trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[0].classes()).toContain('is-selected')
    expect(tempWrapper.find('.photo-inspector-preview').exists()).toBe(true)
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

  it('shows current categories beside their controls without the parent folder row', async () => {
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
    expect(tempWrapper.find('.photo-current-parent').exists()).toBe(false)
    expect(tempWrapper.find('.photo-current-categories').text()).toBe('旅行')

    await tempWrapper.findAll('.photo-open')[1].trigger('click')
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.find('.photo-current-parent').exists()).toBe(false)
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

  it('shows three icon-only batch actions with accessible changing labels', async () => {
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
    expect(tempWrapper.findAll('.photo-batch-actions button')).toHaveLength(3)
    expect(tempWrapper.find('.photo-batch-actions').text()).toBe('')
    expect(tempWrapper.find('[data-testid="select-all-photos"]').attributes('aria-label')).toBe('全选当前结果')
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('title')).toBe('取消选择（0张）')
    expect(tempWrapper.find('[data-testid="batch-delete-photos"]').attributes('aria-label')).toBe('批量删除')
    expect(tempWrapper.find('[data-testid="batch-move-photos"]').exists()).toBe(false)
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('disabled')).toBeDefined()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    expect(tempWrapper.find('[data-testid="select-all-photos"]').attributes('aria-label')).toBe('取消全选')
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('aria-label')).toBe('取消选择（2张）')
    await tempWrapper.find('[data-testid="clear-photo-selection"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.find('[data-testid="clear-photo-selection"]').attributes('aria-label')).toBe('取消选择（0张）')
    tempWrapper.unmount()
  })

  it('filters photos by one category within the current folder and clears hidden selections', async () => {
    const tempCategoryIndex: ResourceIndex = {
      ...DIRECTORY_INDEX,
      metadataById: {
        'photo-1': { kind: 'PHOTO', details: { title: 'family', album: '', date: '', description: '', categories: ['旅行'], rating: null } },
        'photo-2': { kind: 'PHOTO', details: { title: 'second', album: '', date: '', description: '', categories: ['旅行', '节日'], rating: null } },
        'photo-3': { kind: 'PHOTO', details: { title: 'third', album: '', date: '', description: '', categories: ['节日'], rating: null } },
      },
    }
    loadResourceIndex.mockResolvedValue(tempCategoryIndex)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB, ...PHOTO_MOVE_STUBS } },
    })
    await nextTick()
    await nextTick()

    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-category-filter"]').text()).toContain('分类')
    expect(tempWrapper.find('[data-testid="photo-category-option"]').exists()).toBe(false)
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(4)
    await tempWrapper.find('[data-testid="photo-category-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-category-option"]')).toHaveLength(2)
    await tempWrapper.find('[data-testid="photo-category-option"][data-category="旅行"]').trigger('click')

    expect(tempWrapper.find('[data-testid="photo-category-filter"]').text()).toContain('旅行')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.find('.photo-inspector-preview').exists()).toBe(false)
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.find('.photo-title').text()).toContain('second')
    await tempWrapper.find('[data-testid="photo-category-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-category-option"]')).toHaveLength(2)
    await tempWrapper.find('[data-testid="photo-category-all"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-category-filter"]').text()).toContain('分类')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    await tempWrapper.find('[data-testid="choose-child"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-category-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-category-option"]')).toHaveLength(1)
    expect(tempWrapper.find('[data-testid="photo-category-option"]').attributes('data-category')).toBe('节日')
    await tempWrapper.find('[data-testid="photo-category-option"]').trigger('click')
    await tempWrapper.find('[data-testid="choose-root"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-category-filter"]').text()).toContain('分类')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    await tempWrapper.find('[data-testid="photo-category-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-category-option"]')).toHaveLength(1)
    expect(tempWrapper.find('[data-testid="photo-category-option"]').attributes('data-category')).toBe('旅行')
    await tempWrapper.find('[data-testid="photo-category-all"]').trigger('click')
    await tempWrapper.find('[data-testid="choose-all"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-category-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-category-option"]')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(4)
    tempWrapper.unmount()
  })

  it('shows category and rating filters to the left of search input only while search is expanded', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await flushPromises()

    expect(tempWrapper.find('.photo-search-panel').exists()).toBe(false)
    expect(tempWrapper.find('[data-testid="photo-category-filter"]').exists()).toBe(false)
    expect(tempWrapper.find('[data-testid="photo-rating-filter"]').exists()).toBe(false)
    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')

    const tempSearchPanel = tempWrapper.find('.photo-search-panel')
    expect(tempSearchPanel.exists()).toBe(true)
    expect(Array.from(tempSearchPanel.element.children).map((tempElement) => tempElement.className)).toEqual([
      'photo-category-control', 'photo-rating-control', 'photo-search-input',
    ])
    await tempWrapper.find('[data-testid="photo-rating-filter"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-rating-option"][data-rating="3"]').trigger('click')
    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('.photo-search-panel').exists()).toBe(false)
    expect(tempWrapper.find('button[aria-label="搜索照片"]').classes()).toContain('is-active')
    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-rating-filter"]').text()).toContain('3星')
    await tempWrapper.find('[data-testid="photo-rating-filter"]').trigger('click')
    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-rating-option"]').exists()).toBe(false)
    tempWrapper.unmount()
  })

  it('filters photos by exact star rating and shows the active rating beside the icon', async () => {
    const tempRatingIndex: ResourceIndex = {
      ...DIRECTORY_INDEX,
      metadataById: {
        'photo-1': { kind: 'PHOTO', details: { title: 'family', album: '', date: '', description: '', categories: [], rating: 3 } },
        'photo-2': { kind: 'PHOTO', details: { title: 'second', album: '', date: '', description: '', categories: [], rating: 5 } },
        'photo-3': { kind: 'PHOTO', details: { title: 'third', album: '', date: '', description: '', categories: [], rating: 3 } },
      },
    }
    loadResourceIndex.mockResolvedValue(tempRatingIndex)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB, ...PHOTO_MOVE_STUBS } },
    })
    await flushPromises()

    await tempWrapper.find('button[aria-label="搜索照片"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-rating-filter"]').text()).toBe('')
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-rating-filter"]').trigger('click')
    expect(tempWrapper.findAll('[data-testid="photo-rating-option"]')).toHaveLength(5)
    await tempWrapper.find('[data-testid="photo-rating-option"][data-rating="3"]').trigger('click')

    expect(tempWrapper.find('[data-testid="photo-rating-filter"]').text()).toContain('3星')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(0)
    await tempWrapper.find('[data-testid="choose-child"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)

    await tempWrapper.find('[data-testid="photo-rating-filter"]').trigger('click')
    await tempWrapper.find('[data-testid="photo-rating-all"]').trigger('click')
    expect(tempWrapper.find('[data-testid="photo-rating-filter"]').text()).toBe('')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    tempWrapper.unmount()
  })

  it('adds a new category to selected photos without replacing their other metadata', async () => {
    let tempIndex = MULTI_PHOTO_INDEX
    loadResourceIndex.mockResolvedValue(tempIndex)
    saveResourceMetadata.mockImplementation(async (_familyId: number, resourceId: string, metadata: ResourceIndex['metadataById'][string]) => {
      tempIndex = { ...tempIndex, metadataById: { ...tempIndex.metadataById, [resourceId]: metadata } }
      return tempIndex
    })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await flushPromises()

    expect(tempWrapper.find('[data-testid="batch-category-photos"]').attributes('disabled')).toBeDefined()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-category-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-category-input"]').setValue(' 家人 ')
    await tempWrapper.find('[data-testid="batch-category-input"]').trigger('keydown', { key: 'Enter' })
    await flushPromises()

    expect(saveResourceMetadata).toHaveBeenCalledTimes(2)
    expect(saveResourceMetadata).toHaveBeenCalledWith(12, 'photo-1', {
      kind: 'PHOTO', details: { ...MULTI_PHOTO_INDEX.metadataById['photo-1']?.details, categories: ['旅行', '家人'] },
    })
    expect(tempIndex.metadataById['photo-2']).toMatchObject({ kind: 'PHOTO', details: { title: 'second', categories: ['家人'], rating: null } })
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-card .photo-categories').map((categories) => categories.text())).toEqual(['旅行、家人', '家人'])
    expect(tempWrapper.findAll('.photo-card .photo-media > .photo-rating-badge .photo-rating-star')).toHaveLength(4)
    expect(tempWrapper.find('.photo-card .photo-media > .photo-rating-badge').text()).toBe('')
    expect(tempWrapper.find('.photo-card .photo-title .photo-rating-badge').exists()).toBe(false)
    expect(tempWrapper.find('.photo-card .photo-media .photo-format').text()).toBe('JPG')
    tempWrapper.unmount()
  })

  it('offers categories from the current folder for batch tagging', async () => {
    loadResourceIndex.mockResolvedValue(MULTI_PHOTO_INDEX)
    saveResourceMetadata.mockResolvedValue(MULTI_PHOTO_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await flushPromises()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-category-photos"]').trigger('click')

    expect(tempWrapper.findAll('[data-testid="batch-category-option"]')).toHaveLength(1)
    expect(tempWrapper.find('[data-testid="batch-category-option"]').text()).toBe('旅行')
    await tempWrapper.find('[data-testid="batch-category-option"]').trigger('click')
    await flushPromises()
    expect(saveResourceMetadata).toHaveBeenCalledWith(12, 'photo-2', expect.objectContaining({
      kind: 'PHOTO', details: expect.objectContaining({ categories: ['旅行'] }),
    }))
    tempWrapper.unmount()
  })

  it('overwrites only the selected rating and keeps every photo selected after a partial failure', async () => {
    let tempIndex = MULTI_PHOTO_INDEX
    loadResourceIndex.mockResolvedValue(tempIndex)
    saveResourceMetadata.mockImplementation(async (_familyId: number, resourceId: string, metadata: ResourceIndex['metadataById'][string]) => {
      if (resourceId === 'photo-2') throw new Error('保存失败')
      tempIndex = { ...tempIndex, metadataById: { ...tempIndex.metadataById, [resourceId]: metadata } }
      return tempIndex
    })
    const tempWarning = vi.spyOn(ElMessage, 'warning')
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await flushPromises()

    expect(tempWrapper.find('[data-testid="batch-rating-photos"]').attributes('disabled')).toBeDefined()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-option"][data-rating="5"]').trigger('click')
    await flushPromises()

    expect(saveResourceMetadata).toHaveBeenCalledTimes(2)
    expect(tempIndex.metadataById['photo-1']).toMatchObject({ kind: 'PHOTO', details: { title: '一二三四五六七八九十甲乙丙丁戊己', categories: ['旅行'], rating: 5 } })
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-card .photo-rating-badge .photo-rating-star')).toHaveLength(5)
    expect(tempWarning).toHaveBeenCalledWith(expect.stringContaining('1 张成功，1 张失败'))
    tempWarning.mockRestore()
    tempWrapper.unmount()
  })

  it('blocks selection and folder history while batch metadata is saving', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    let tempResolveSave!: (index: ResourceIndex) => void
    saveResourceMetadata.mockImplementationOnce(() => new Promise<ResourceIndex>((resolve) => { tempResolveSave = resolve }))
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB, ...PHOTO_MOVE_STUBS } },
    })
    await flushPromises()
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="choose-trip2"]').trigger('click')
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-option"][data-rating="4"]').trigger('click')

    expect(tempWrapper.find('[data-testid="select-all-photos"]').attributes('disabled')).toBeDefined()
    expect(tempWrapper.find('[data-testid="batch-category-photos"]').attributes('disabled')).toBeDefined()
    expect(tempWrapper.find('[aria-label="返回上一个文件夹"]').attributes('disabled')).toBeDefined()
    expect(tempWrapper.find('[aria-label="前进到下一个文件夹"]').attributes('disabled')).toBeDefined()
    tempResolveSave(DIRECTORY_INDEX)
    await flushPromises()
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('trip2')
    tempWrapper.unmount()
  })

  it('stops a pending batch before writing more photos after the family changes', async () => {
    const tempOtherFamilyIndex: ResourceIndex = {
      ...PHOTO_INDEX, familyId: 13, roots: { ...PHOTO_INDEX.roots, photos: [] }, resources: [], photoDirectories: [],
    }
    loadResourceIndex.mockImplementation(async (familyId: number) => familyId === 12 ? MULTI_PHOTO_INDEX : tempOtherFamilyIndex)
    let tempResolveSave!: (index: ResourceIndex) => void
    saveResourceMetadata.mockImplementationOnce(() => new Promise<ResourceIndex>((resolve) => { tempResolveSave = resolve }))
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' }, global: { stubs: PHOTO_MOVE_STUBS } })
    await flushPromises()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-photos"]').trigger('click')
    await tempWrapper.find('[data-testid="batch-rating-option"][data-rating="2"]').trigger('click')

    TEST_AUTH_STORE.user.familyId = 13
    await flushPromises()
    tempResolveSave(MULTI_PHOTO_INDEX)
    await flushPromises()

    expect(saveResourceMetadata).toHaveBeenCalledTimes(1)
    expect(saveResourceMetadata).toHaveBeenCalledWith(12, 'photo-1', expect.anything())
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(0)
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

  it('creates a child of the selected tree folder without moving selected photos', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    createPhotoSubfolder.mockResolvedValue({
      ...DIRECTORY_INDEX, photoDirectories: [...DIRECTORY_INDEX.photoDirectories, 'D:/photos/新相册'],
    })
    const tempPrompt = vi.spyOn(ElMessageBox, 'prompt')
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' }, global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()
    await tempWrapper.find('[data-testid="choose-root"]').trigger('click')
    await tempWrapper.find('[data-testid="create-subfolder"]').trigger('click')
    await flushPromises()

    expect(createPhotoSubfolder).toHaveBeenCalledWith(12, 'D:/photos', '新相册')
    expect(tempPrompt).not.toHaveBeenCalled()
    expect(tempWrapper.find('[data-testid="batch-move-photos"]').exists()).toBe(false)
    expect(movePhotos).not.toHaveBeenCalled()
    tempPrompt.mockRestore()
    tempWrapper.unmount()
  })

  it('drags the selected photo set to the tree and reuses the existing move action', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    movePhotos.mockResolvedValue({ ...DIRECTORY_INDEX, resources: DIRECTORY_INDEX.resources.map((resource) => ({
      ...resource, path: `D:/photos/empty/${resource.name}`,
    })) })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await flushPromises()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    const tempDragData = { setData: vi.fn(), effectAllowed: '' }

    expect(tempWrapper.findAll('.photo-card').every((card) => card.attributes('draggable') === 'true')).toBe(true)
    await tempWrapper.findAll('.photo-card')[0].trigger('dragstart', { dataTransfer: tempDragData })
    expect(tempWrapper.findComponent(PhotoDirectoryTree).props('draggingPhotos')).toBe(true)
    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('dropPhotos', 'D:/photos/empty')
    await flushPromises()

    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-1', 'photo-2', 'photo-3', 'photo-4'], 'D:/photos/empty', null)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.findComponent(PhotoDirectoryTree).props('draggingPhotos')).toBe(false)
    tempWrapper.unmount()
  })

  it('lets selected photos drop directly into a manually added root', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    movePhotos.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await flushPromises()
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.findAll('.photo-card')[1].trigger('dragstart', { dataTransfer: { setData: vi.fn(), effectAllowed: '' } })

    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('dropPhotos', 'D:/photos')
    await flushPromises()

    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-1', 'photo-2', 'photo-3', 'photo-4'], 'D:/photos', null)
    tempWrapper.unmount()
  })

  it('drags an unselected photo without requiring a prior click', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    movePhotos.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await flushPromises()

    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    expect(tempWrapper.findAll('.photo-card')[1].attributes('draggable')).toBe('true')
    const tempDragData = { setData: vi.fn(), effectAllowed: '' }
    await tempWrapper.findAll('.photo-card')[1].trigger('dragstart', { dataTransfer: tempDragData })
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[1].classes()).toContain('is-selected')
    expect(tempDragData.effectAllowed).toBe('move')
    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('dropPhotos', 'D:/photos/empty')
    await flushPromises()

    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-3'], 'D:/photos/empty', null)
    tempWrapper.unmount()
  })

  it('replaces prior selection when dragging another photo and preserves that photo if moving fails', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    movePhotos.mockRejectedValue(new Error('目标文件夹已存在同名照片'))
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await flushPromises()
    vi.useFakeTimers()
    await tempWrapper.findAll('.photo-open')[0].trigger('click')
    await vi.advanceTimersByTimeAsync(260)

    await tempWrapper.findAll('.photo-open')[1].trigger('click')
    await tempWrapper.findAll('.photo-card')[1].trigger('dragstart', { dataTransfer: { setData: vi.fn(), effectAllowed: '' } })
    await vi.advanceTimersByTimeAsync(260)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[1].classes()).toContain('is-selected')
    expect(tempWrapper.findComponent(PhotoDirectoryTree).props('draggingPhotos')).toBe(true)
    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('dropPhotos', 'D:/photos/empty')
    await flushPromises()
    expect(movePhotos).toHaveBeenCalledWith(12, ['photo-3'], 'D:/photos/empty', null)
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-card')[1].classes()).toContain('is-selected')
    tempWrapper.unmount()
  })

  it('does not call the move command when every dragged photo is already in the target folder', async () => {
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' }, global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="select-all-photos"]').trigger('click')
    await tempWrapper.find('.photo-card').trigger('dragstart', { dataTransfer: { setData: vi.fn(), effectAllowed: '' } })

    tempWrapper.findComponent(PhotoDirectoryTree).vm.$emit('dropPhotos', 'D:/photos/trip')
    await flushPromises()
    expect(movePhotos).not.toHaveBeenCalled()
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(1)
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

  it('shows only direct photos in a selected directory and clears hidden selections', async () => {
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
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.find('.photo-title').text()).toContain('second')
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('D:/photos/trip')
    expect(tempWrapper.findAll('.photo-card.is-selected')).toHaveLength(0)
    await tempWrapper.find('[data-testid="choose-child"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.find('.photo-title').text()).toContain('third')
    await tempWrapper.find('[data-testid="choose-root"]').trigger('click')
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.find('.photo-title').text()).toContain('family')
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

  it('lays out each month in justified rows and responds to the wall width and zoom', async () => {
    const tempResizeObserver = { callback: null as ResizeObserverCallback | null }
    vi.stubGlobal('ResizeObserver', class {
      constructor(callback: ResizeObserverCallback) { tempResizeObserver.callback = callback }
      observe() {}
      disconnect() {}
    })
    const tempRatios = [[2, 1], [1, 1], [1, 2], [1, 1], [1, 1]]
    const tempResources = tempRatios.map(([imageWidth, imageHeight], index) => ({
      ...PHOTO_INDEX.resources[0], id: `photo-${index}`, path: `D:/photos/${index}.jpg`,
      name: `${index}.jpg`, imageWidth, imageHeight,
    }))
    loadResourceIndex.mockResolvedValue({ ...PHOTO_INDEX, resources: tempResources })
    const tempWrapper = shallowMount(ResourceLibraryView, { props: { kind: 'PHOTO' } })
    await flushPromises()
    tempResizeObserver.callback?.([{ contentRect: { width: 600 } } as ResizeObserverEntry], {} as ResizeObserver)
    await nextTick()

    expect(tempWrapper.findAll('.photo-row')).toHaveLength(2)
    expect(tempWrapper.findAll('.photo-row')[0].findAll('.photo-card')).toHaveLength(4)
    expect(tempWrapper.findAll('.photo-row')[0].attributes('style')).toContain('--photo-row-height: 125.333')
    expect(tempWrapper.findAll('.photo-row')[0].find('.photo-card').attributes('style')).toContain('width: 250.666')
    expect(tempWrapper.findAll('.photo-row')[1].attributes('style')).toContain('--photo-row-height: 140px')

    await tempWrapper.find('[aria-label="放大照片缩略图"]').trigger('click')
    expect(tempWrapper.findAll('.photo-row')[1].attributes('style')).toContain('--photo-row-height: 160px')
    tempResizeObserver.callback?.([{ contentRect: { width: 320 } } as ResizeObserverEntry], {} as ResizeObserver)
    await nextTick()
    expect(tempWrapper.findAll('.photo-row')[0].findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.findAll('.photo-row')[0].find('.photo-card').attributes('style')).toContain('width: 320px')
    tempWrapper.unmount()
    vi.unstubAllGlobals()
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
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
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

  it('keeps an added directory when the removal confirmation is cancelled', async () => {
    loadResourceIndex.mockResolvedValue(PHOTO_INDEX)
    scanResources.mockClear()
    const tempConfirm = vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce(new Error('cancelled'))
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()

    await tempWrapper.find('[data-testid="choose-root"]').trigger('click')
    await tempWrapper.find('[data-testid="remove-directory"]').trigger('click')
    await flushPromises()

    expect(tempConfirm).toHaveBeenCalledOnce()
    expect(scanResources).not.toHaveBeenCalled()
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('D:/photos')
    tempConfirm.mockRestore()
    tempWrapper.unmount()
  })

  it('removes only the confirmed added root from the index and refreshes the gallery', async () => {
    const tempOtherPhoto = { ...PHOTO_INDEX.resources[0], id: 'other-photo', path: 'E:/other/other.jpg', name: 'other.jpg' }
    const tempTwoRootIndex: ResourceIndex = {
      ...PHOTO_INDEX,
      roots: { ...PHOTO_INDEX.roots, photos: ['D:/photos', 'E:/other'] },
      photoDirectories: ['D:/photos', 'D:/photos/trip', 'E:/other'],
      resources: [...PHOTO_INDEX.resources, tempOtherPhoto],
    }
    loadResourceIndex.mockResolvedValue(tempTwoRootIndex)
    scanResources.mockClear()
    recyclePhoto.mockClear()
    scanResources.mockResolvedValue({
      ...tempTwoRootIndex,
      roots: { ...tempTwoRootIndex.roots, photos: ['E:/other'] },
      photoDirectories: ['E:/other'],
      resources: [tempOtherPhoto],
    })
    const tempConfirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValueOnce(undefined as never)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()

    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="remove-directory"]').trigger('click')
    expect(tempConfirm).not.toHaveBeenCalled()
    await tempWrapper.find('[data-testid="choose-root"]').trigger('click')
    await tempWrapper.find('[data-testid="remove-directory"]').trigger('click')
    await flushPromises()

    expect(tempConfirm).toHaveBeenCalledOnce()
    expect(scanResources).toHaveBeenCalledWith(12, 'PHOTO', ['E:/other'])
    expect(recyclePhoto).not.toHaveBeenCalled()
    expect(tempWrapper.findAll('.photo-card')).toHaveLength(1)
    expect(tempWrapper.find('.photo-title').text()).toContain('other')
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('全部照片')
    tempConfirm.mockRestore()
    tempWrapper.unmount()
  })

  it('leaves a removed nested root selection even when the parent root still scans that folder', async () => {
    const tempNestedRootIndex: ResourceIndex = {
      ...DIRECTORY_INDEX,
      roots: { ...DIRECTORY_INDEX.roots, photos: ['D:/photos', 'D:/photos/trip'] },
    }
    loadResourceIndex.mockResolvedValue(tempNestedRootIndex)
    scanResources.mockResolvedValue({
      ...tempNestedRootIndex,
      roots: { ...tempNestedRootIndex.roots, photos: ['D:/photos'] },
    })
    const tempConfirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValueOnce(undefined as never)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' },
      global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()

    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="remove-directory"]').trigger('click')
    await flushPromises()

    expect(scanResources).toHaveBeenCalledWith(12, 'PHOTO', ['D:/photos'])
    expect(tempWrapper.find('.photo-current-directory').text()).toBe('全部照片')
    tempConfirm.mockRestore()
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

describe('photo subfolder deletion', () => {
  it('confirms before moving a subfolder to the recycle bin and updates the index', async () => {
    TEST_AUTH_STORE.user.familyId = 12
    loadResourceIndex.mockResolvedValue(DIRECTORY_INDEX)
    recyclePhotoDirectory.mockResolvedValue({
      ...DIRECTORY_INDEX,
      photoDirectories: ['D:/photos', 'D:/photos/trip2', 'D:/photos/empty'],
      resources: DIRECTORY_INDEX.resources.filter((resource) => !resource.path.includes('/trip/')),
    })
    const tempConfirm = vi.spyOn(ElMessageBox, 'confirm').mockResolvedValueOnce(undefined as never)
    const tempWrapper = shallowMount(ResourceLibraryView, {
      props: { kind: 'PHOTO' }, global: { stubs: { PhotoDirectoryTree: PHOTO_TREE_STUB } },
    })
    await flushPromises()
    await tempWrapper.find('[data-testid="choose-trip"]').trigger('click')
    await tempWrapper.find('[data-testid="delete-subfolder"]').trigger('click')
    await flushPromises()
    expect(tempConfirm).toHaveBeenCalledOnce()
    expect(recyclePhotoDirectory).toHaveBeenCalledWith(12, 'D:/photos/trip')
    expect(tempWrapper.find('.directory-stub').attributes('data-selected')).toBe('all')
    tempConfirm.mockRestore()
    tempWrapper.unmount()
  })
})
