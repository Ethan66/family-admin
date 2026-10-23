<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Check, Close, CollectionTag, Delete, Film, FolderAdd, FolderOpened, Minus, Plus, Refresh, Search, Star, StarFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import {
  loadResourceIndex,
  listPhotoRootJsonFiles,
  createPhotoSubfolder,
  movePhotos,
  openWithDefaultApp,
  readBinaryPreview,
  readTextPreview,
  recyclePhoto,
  recyclePhotoDirectory,
  revealInExplorer,
  scanResources,
  saveResourceMetadata,
  selectResourceDirectories,
} from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import type { ExamDetails, MovieDetails, PhotoDetails, ResourceEntry, ResourceIndex, ResourceKind, ResourceMetadata } from '@/types/resource'
import { buildPhotoDirectoryTree, isPhotoDirectlyInDirectory, type PhotoDirectoryNode } from '@/utils/photoDirectory'
import { buildPhotoRows } from '@/utils/photoWall'
import { formatFileSize, resolveActiveFamilyId } from '@/utils/resource'
import LocalPhotoThumbnail from './LocalPhotoThumbnail.vue'
import PhotoDirectoryTree from './PhotoDirectoryTree.vue'

interface Props {
  kind: ResourceKind
}
type PhotoBatchChange = { type: 'category'; category: string } | { type: 'rating'; rating: number }

const MIN_PHOTO_THUMBNAIL_SIZE = 100
const MAX_PHOTO_THUMBNAIL_SIZE = 240
const PHOTO_THUMBNAIL_STEP = 20
const PHOTO_RATING_OPTIONS = [1, 2, 3, 4, 5]
const PHOTO_WALL_COLUMN_GAP = 12

const props = defineProps<Props>()
const authStore = useAuthStore()
const resourceIndexData = ref<ResourceIndex | null>(null)
const photoRootJsonPathsData = ref<string[]>([])
const keyword = ref('')
const isPhotoSearchVisible = ref(false)
const activePhotoCategory = ref<string | null>(null)
const isPhotoCategoryMenuVisible = ref(false)
const activePhotoRating = ref<number | null>(null)
const isPhotoRatingMenuVisible = ref(false)
const isBatchCategoryMenuVisible = ref(false)
const isBatchRatingMenuVisible = ref(false)
const batchPhotoCategoryInput = ref('')
const activeExtension = ref('')
const isMetadataVisible = ref(false)
const isSavingMetadata = ref(false)
const editingResource = ref<ResourceEntry | null>(null)
const photoForm = ref<PhotoDetails>({ title: '', album: '', date: '', description: '', categories: [], rating: null })
const movieForm = ref<MovieDetails>({ title: '', movieType: '电影', year: null, director: '', minutes: null, rating: null, status: '想看', progress: 0, description: '' })
const examForm = ref<ExamDetails>({ title: '', subject: '数学', grade: '初二', date: '', fullScore: 100, score: null, status: '待批改', note: '' })
const movieTypes = ['电影', '纪录片', '动画', '剧集', '家庭录像']
const movieStatuses = ['想看', '在看', '已看']
const subjects = ['语文', '数学', '英语', '物理', '化学', '历史', '地理', '生物']
const grades = ['初一', '初二', '初三', '高一', '高二', '高三']
const examStatuses = ['已批改', '待批改', '已归档']
const isLoading = ref(false)
const isScanning = ref(false)
const deletingPhotoId = ref<string | null>(null)
const selectedPhotoIds = ref<string[]>([])
const draggedPhotoIds = ref<string[]>([])
const photoClickTimers = new Map<string, ReturnType<typeof setTimeout>>()
const activePhotoDirectory = ref<string | null>(null)
const photoDirectoryHistory = ref<Array<string | null>>([null])
const photoHistoryIndex = ref(0)
const photoThumbnailSize = ref(140)
const photoWallRef = ref<HTMLElement | null>(null)
const photoWallWidth = ref(800)
let photoWallObserver: ResizeObserver | null = null
const isBatchDeleting = ref(false)
const isBatchMetadataSaving = ref(false)
const isMovingPhotos = ref(false)
const isCreatingPhotoSubfolder = ref(false)
const isDeletingPhotoDirectory = ref(false)
const errorMessage = ref('')
const isPreviewVisible = ref(false)
const previewTitle = ref('')
const previewUrl = ref('')
const previewHtml = ref('')
const previewText = ref('')
const activeFamilyId = computed(() => authStore.user
  ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId)
  : null)
const allResources = computed(() => {
  return resourceIndexData.value?.resources.filter((resource) => resource.kind === props.kind) ?? []
})
const photoDirectoryTree = computed(() => resourceIndexData.value ? buildPhotoDirectoryTree(resourceIndexData.value) : [])
const photoDirectoryOptions = computed(() => {
  const options: Array<{ path: string; label: string; isRoot: boolean }> = []
  function collect(nodes: PhotoDirectoryNode[], depth: number): void {
    for (const node of nodes) {
      options.push({ path: node.path, label: `${'　'.repeat(depth)}${node.name}`, isRoot: depth === 0 })
      collect(node.children, depth + 1)
    }
  }
  collect(photoDirectoryTree.value, 0)
  return options
})
const activePhotoDirectoryName = computed(() => activePhotoDirectory.value?.split(/[\\/]/).filter(Boolean).at(-1) ?? '全部照片')
const extensions = computed(() => [...new Set(allResources.value.map((resource) => resource.extension))].sort())
function metadataFor(resource: ResourceEntry): ResourceMetadata | undefined {
  return resourceIndexData.value?.metadataById?.[resource.id]
}
function photoDetails(resource: ResourceEntry): PhotoDetails | undefined {
  const metadata = metadataFor(resource)
  return metadata?.kind === 'PHOTO' ? metadata.details : undefined
}
const photoCategoryOptions = computed(() => {
  const tempCategories = new Set<string>()
  for (const tempResource of allResources.value) {
    if (tempResource.kind !== 'PHOTO') continue
    for (const tempCategory of photoDetails(tempResource)?.categories ?? []) tempCategories.add(tempCategory)
  }
  return [...tempCategories].sort()
})
const photoFilterCategoryOptions = computed(() => {
  if (activePhotoDirectory.value === null) return photoCategoryOptions.value
  const tempCategories = new Set<string>()
  for (const tempResource of allResources.value) {
    if (tempResource.kind !== 'PHOTO' || !isPhotoDirectlyInDirectory(tempResource.path, activePhotoDirectory.value)) continue
    for (const tempCategory of photoDetails(tempResource)?.categories ?? []) tempCategories.add(tempCategory)
  }
  return [...tempCategories].sort()
})
const selectedPhoto = computed(() => {
  if (props.kind !== 'PHOTO' || !editingResource.value || !selectedPhotoIds.value.includes(editingResource.value.id)) return null
  return allResources.value.find((resource) => resource.id === editingResource.value?.id) ?? null
})
function movieDetails(resource: ResourceEntry): MovieDetails | undefined {
  const metadata = metadataFor(resource)
  return metadata?.kind === 'MOVIE' ? metadata.details : undefined
}
function examDetails(resource: ResourceEntry): ExamDetails | undefined {
  const metadata = metadataFor(resource)
  return metadata?.kind === 'EXAM' ? metadata.details : undefined
}
function displayTitle(resource: ResourceEntry): string {
  const metadata = metadataFor(resource)
  return metadata ? metadata.details.title : resource.name.replace(/\.[^.]+$/, '')
}
function displayPhotoTitle(resource: ResourceEntry): string {
  const tempCharacters = Array.from(displayTitle(resource))
  return tempCharacters.length > 15 ? `${tempCharacters.slice(0, 15).join('')}...` : tempCharacters.join('')
}
function filterValue(resource: ResourceEntry): string {
  const metadata = metadataFor(resource)
  if (props.kind === 'MOVIE') return metadata?.kind === 'MOVIE' ? metadata.details.movieType : ''
  if (props.kind === 'EXAM') return metadata?.kind === 'EXAM' ? metadata.details.subject : ''
  return resource.extension
}
const filterOptions = computed(() => {
  if (props.kind === 'MOVIE') return movieTypes
  if (props.kind === 'EXAM') return subjects
  return extensions.value
})
const resources = computed(() => {
  const search = keyword.value.trim().toLowerCase()
  return allResources.value.filter((resource) => {
    const metadata = metadataFor(resource)
    const searchable = `${resource.name} ${resource.path} ${metadata ? JSON.stringify(metadata.details) : ''}`.toLowerCase()
    return (!search || search.split(/\s+/).every((part) => searchable.includes(part)))
      && (!activeExtension.value || filterValue(resource) === activeExtension.value)
      && (props.kind !== 'PHOTO' || activePhotoDirectory.value === null || isPhotoDirectlyInDirectory(resource.path, activePhotoDirectory.value))
      && (props.kind !== 'PHOTO' || activePhotoCategory.value === null || photoDetails(resource)?.categories.includes(activePhotoCategory.value))
      && (props.kind !== 'PHOTO' || activePhotoRating.value === null || photoDetails(resource)?.rating === activePhotoRating.value)
  })
})
const photoGroups = computed(() => {
  const tempGroups = new Map<string, ResourceEntry[]>()
  for (const tempResource of resources.value) {
    const tempMonth = photoCaptureDateTime(tempResource).slice(0, 7)
    tempGroups.set(tempMonth, [...(tempGroups.get(tempMonth) ?? []), tempResource])
  }
  return [...tempGroups]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([tempMonth, tempResources]) => [tempMonth, tempResources.sort((a, b) =>
      photoCaptureDateTime(b).localeCompare(photoCaptureDateTime(a)) || a.path.localeCompare(b.path))] as const)
})
const photoLayoutGroups = computed(() => photoGroups.value.map(([month, monthResources]) => ({
  month,
  count: monthResources.length,
  rows: buildPhotoRows(monthResources, photoWallWidth.value, photoThumbnailSize.value, PHOTO_WALL_COLUMN_GAP),
})))

/** 没有 EXIF 拍摄时间时，用文件修改时间对应的本地时间参与归档与排序。 */
function photoCaptureDateTime(resource: ResourceEntry): string {
  if (resource.capturedAt) return resource.capturedAt
  const tempModified = new Date(resource.modifiedAt)
  const tempYear = tempModified.getFullYear()
  const tempMonth = String(tempModified.getMonth() + 1).padStart(2, '0')
  const tempDay = String(tempModified.getDate()).padStart(2, '0')
  const tempHour = String(tempModified.getHours()).padStart(2, '0')
  const tempMinute = String(tempModified.getMinutes()).padStart(2, '0')
  const tempSecond = String(tempModified.getSeconds()).padStart(2, '0')
  return `${tempYear}-${tempMonth}-${tempDay}T${tempHour}:${tempMinute}:${tempSecond}`
}
const rootKey = computed(() => ({ PHOTO: 'photos', MOVIE: 'movies', NOTE: 'notes', EXAM: 'exams' } as const)[props.kind])
const roots = computed(() => resourceIndexData.value?.roots[rootKey.value] ?? [])

/** 索引重扫后保留仍存在的目录；已删除的目录不继续过滤图库。 */
function reconcileActivePhotoDirectory(nodes: PhotoDirectoryNode[]): void {
  const tempPaths = new Set<string>()
  function collectPaths(directories: PhotoDirectoryNode[]): void {
    for (const tempDirectory of directories) {
      tempPaths.add(tempDirectory.path)
      collectPaths(tempDirectory.children)
    }
  }
  collectPaths(nodes)
  if (activePhotoDirectory.value !== null && !tempPaths.has(activePhotoDirectory.value)) {
    handleSelectPhotoDirectory(null, false)
    photoDirectoryHistory.value = [null]
    photoHistoryIndex.value = 0
    return
  }
  const tempPreviousHistory = photoDirectoryHistory.value
  const tempCurrentIndex = photoHistoryIndex.value
  photoDirectoryHistory.value = tempPreviousHistory.filter((path) => path === null || tempPaths.has(path))
  photoHistoryIndex.value = tempPreviousHistory.slice(0, tempCurrentIndex + 1)
    .filter((path) => path === null || tempPaths.has(path)).length - 1
}

/** 树选择新增浏览历史；历史跳转不重复记录，并清理旧目录的照片选择。 */
function handleSelectPhotoDirectory(path: string | null, recordHistory = true): void {
  if (isBatchMetadataSaving.value) return
  if (activePhotoDirectory.value === path) return
  clearPhotoClickTimer()
  activePhotoDirectory.value = path
  selectedPhotoIds.value = []
  editingResource.value = null
  if (!recordHistory) return
  photoDirectoryHistory.value = [...photoDirectoryHistory.value.slice(0, photoHistoryIndex.value + 1), path]
  photoHistoryIndex.value = photoDirectoryHistory.value.length - 1
}

/** 切换分类时清空旧结果的选中状态，避免批量操作作用于不可见照片。 */
function handleSelectPhotoCategory(category: string | null): void {
  if (isBatchMetadataSaving.value) return
  isPhotoCategoryMenuVisible.value = false
  if (activePhotoCategory.value === category) return
  clearPhotoClickTimer()
  activePhotoCategory.value = category
  selectedPhotoIds.value = []
  editingResource.value = null
}

function onPhotoCategoryFocusOut(event: FocusEvent): void {
  const tempNextTarget = event.relatedTarget
  if (tempNextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(tempNextTarget)) return
  isPhotoCategoryMenuVisible.value = false
}

/** 切换评分时清空旧结果的选中状态，避免批量操作作用于不可见照片。 */
function handleSelectPhotoRating(rating: number | null): void {
  if (isBatchMetadataSaving.value) return
  isPhotoRatingMenuVisible.value = false
  if (activePhotoRating.value === rating) return
  clearPhotoClickTimer()
  activePhotoRating.value = rating
  selectedPhotoIds.value = []
  editingResource.value = null
}

function onPhotoRatingFocusOut(event: FocusEvent): void {
  const tempNextTarget = event.relatedTarget
  if (tempNextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(tempNextTarget)) return
  isPhotoRatingMenuVisible.value = false
}

/** 收起搜索区时只关闭浮层，保留关键词及分类、评分筛选条件。 */
function onTogglePhotoSearch(): void {
  isPhotoSearchVisible.value = !isPhotoSearchVisible.value
  if (isPhotoSearchVisible.value) return
  isPhotoCategoryMenuVisible.value = false
  isPhotoRatingMenuVisible.value = false
}

function onPhotoHistoryBack(): void {
  if (photoHistoryIndex.value === 0 || isBatchMetadataSaving.value) return
  photoHistoryIndex.value -= 1
  handleSelectPhotoDirectory(photoDirectoryHistory.value[photoHistoryIndex.value], false)
}

function onPhotoHistoryForward(): void {
  if (photoHistoryIndex.value >= photoDirectoryHistory.value.length - 1 || isBatchMetadataSaving.value) return
  photoHistoryIndex.value += 1
  handleSelectPhotoDirectory(photoDirectoryHistory.value[photoHistoryIndex.value], false)
}

function onPhotoZoomOut(): void {
  photoThumbnailSize.value = Math.max(MIN_PHOTO_THUMBNAIL_SIZE, photoThumbnailSize.value - PHOTO_THUMBNAIL_STEP)
}

function onPhotoZoomIn(): void {
  photoThumbnailSize.value = Math.min(MAX_PHOTO_THUMBNAIL_SIZE, photoThumbnailSize.value + PHOTO_THUMBNAIL_STEP)
}

function clearPhotoClickTimer(resourceId?: string): void {
  if (resourceId) {
    const tempTimer = photoClickTimers.get(resourceId)
    if (tempTimer) clearTimeout(tempTimer)
    photoClickTimers.delete(resourceId)
    return
  }
  for (const tempTimer of photoClickTimers.values()) clearTimeout(tempTimer)
  photoClickTimers.clear()
}

/** 未编辑过的照片采用文件名作标题，批量保存其他字段时不会触发原图改名。 */
function editablePhotoDetails(resource: ResourceEntry): PhotoDetails {
  const tempExisting = photoDetails(resource)
  return tempExisting ? { ...tempExisting, categories: [...tempExisting.categories] } : {
    title: resource.name.replace(/\.[^.]+$/, ''), album: '', date: resource.modifiedAt.slice(0, 10),
    description: '', categories: [], rating: null,
  }
}

function showPhotoInInspector(resource: ResourceEntry): void {
  editingResource.value = resource
  photoForm.value = editablePhotoDetails(resource)
}

function showLastSelectedPhoto(): void {
  const tempLastId = selectedPhotoIds.value.at(-1)
  const tempLastPhoto = allResources.value.find((resource) => resource.id === tempLastId)
  if (tempLastPhoto) showPhotoInInspector(tempLastPhoto)
  else editingResource.value = null
}

/** 已选照片单击即取消该张；未选照片普通单击独选，Ctrl 单击追加多选。 */
function handlePhotoSelection(resource: ResourceEntry, isCtrlPressed: boolean): void {
  if (isBatchDeleting.value || isMovingPhotos.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  if (selectedPhotoIds.value.includes(resource.id)) {
    selectedPhotoIds.value = selectedPhotoIds.value.filter((id) => id !== resource.id)
    if (editingResource.value?.id === resource.id) showLastSelectedPhoto()
    return
  }
  if (!isCtrlPressed) {
    selectedPhotoIds.value = [resource.id]
    showPhotoInInspector(resource)
    return
  }
  selectedPhotoIds.value = [...selectedPhotoIds.value, resource.id]
  showPhotoInInspector(resource)
}

function onPhotoClick(resource: ResourceEntry, event: MouseEvent): void {
  clearPhotoClickTimer(resource.id)
  const tempIsCtrlPressed = event.ctrlKey
  photoClickTimers.set(resource.id, setTimeout(() => {
    photoClickTimers.delete(resource.id)
    handlePhotoSelection(resource, tempIsCtrlPressed)
  }, 210))
}

function onPhotoDoubleClick(resource: ResourceEntry): void {
  clearPhotoClickTimer(resource.id)
  void handleOpenResource(resource)
}

/** 未选照片直接拖动时独选该张；已选照片拖动时固定原有多选集合。 */
function onPhotoDragStart(resource: ResourceEntry, event: DragEvent): void {
  if (isBatchDeleting.value || isMovingPhotos.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value || isScanning.value || isSavingMetadata.value || deletingPhotoId.value !== null) {
    event.preventDefault()
    return
  }
  clearPhotoClickTimer()
  if (!selectedPhotoIds.value.includes(resource.id)) {
    selectedPhotoIds.value = [resource.id]
    showPhotoInInspector(resource)
  }
  draggedPhotoIds.value = [...selectedPhotoIds.value]
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('application/x-family-admin-photos', 'selected')
  }
}

function onPhotoDragEnd(): void {
  draggedPhotoIds.value = []
}

/** 仅将当前拖动的照片投放到已扫描的目录；失败时沿用原移动流程保留选择。 */
function handleDropPhotos(path: string): void {
  const tempPhotoIds = [...draggedPhotoIds.value]
  draggedPhotoIds.value = []
  if (!tempPhotoIds.length || !photoDirectoryOptions.value.some((option) => option.path === path)) return
  if (tempPhotoIds.every((id) => {
    const tempPhoto = allResources.value.find((resource) => resource.id === id)
    return tempPhoto !== undefined && isPhotoDirectlyInDirectory(tempPhoto.path, path)
  })) {
    ElMessage.info('所选照片已在该文件夹中')
    return
  }
  void handleMovePhotos(tempPhotoIds, path, null)
}

function beginEditMetadata(resource: ResourceEntry): void {
  const existing = metadataFor(resource)
  editingResource.value = resource
  const defaultDate = resource.modifiedAt.slice(0, 10)
  if (props.kind === 'PHOTO') {
    if (!selectedPhotoIds.value.includes(resource.id)) selectedPhotoIds.value = [...selectedPhotoIds.value, resource.id]
    showPhotoInInspector(resource)
    return
  } else if (props.kind === 'MOVIE') {
    movieForm.value = existing?.kind === 'MOVIE' ? { ...existing.details } : {
      title: resource.name.replace(/\.[^.]+$/, ''), movieType: '电影', year: null, director: '', minutes: null, rating: null, status: '想看', progress: 0, description: '',
    }
  } else if (props.kind === 'EXAM') {
    examForm.value = existing?.kind === 'EXAM' ? { ...existing.details } : {
      title: resource.name.replace(/\.[^.]+$/, ''), subject: '数学', grade: '初二', date: defaultDate, fullScore: 100, score: null, status: '待批改', note: '',
    }
  }
  isMetadataVisible.value = true
}

async function persistMetadata(): Promise<void> {
  if (activeFamilyId.value === null || !editingResource.value || isSavingMetadata.value || isMovingPhotos.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  const familyId = activeFamilyId.value
  const metadata: ResourceMetadata = props.kind === 'PHOTO'
    ? { kind: 'PHOTO', details: { ...photoForm.value, categories: [...new Set(photoForm.value.categories.map((category) => category.trim()).filter(Boolean))] } }
    : props.kind === 'MOVIE'
      ? { kind: 'MOVIE', details: { ...movieForm.value } }
      : { kind: 'EXAM', details: { ...examForm.value } }
  if (!metadata.details.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  isSavingMetadata.value = true
  try {
    const tempPreviousPhotoIds = new Set(resourceIndexData.value?.resources.map((resource) => resource.id))
    const savedIndex = await saveResourceMetadata(familyId, editingResource.value.id, metadata)
    if (activeFamilyId.value !== familyId) return
    resourceIndexData.value = savedIndex
    if (metadata.kind === 'PHOTO') {
      const tempSavedPhoto = savedIndex.resources.find((resource) => resource.id === editingResource.value?.id)
        ?? savedIndex.resources.find((resource) => resource.kind === 'PHOTO' && !tempPreviousPhotoIds.has(resource.id))
      if (tempSavedPhoto && editingResource.value.id !== tempSavedPhoto.id) {
        selectedPhotoIds.value = selectedPhotoIds.value.map((id) => id === editingResource.value?.id ? tempSavedPhoto.id : id)
      }
      if (tempSavedPhoto) showPhotoInInspector(tempSavedPhoto)
    } else {
      isMetadataVisible.value = false
    }
    ElMessage.success(metadata.kind === 'PHOTO' ? '照片资料已保存' : '资料已保存到当前家庭的本地 JSON')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    isSavingMetadata.value = false
  }
}

async function loadIndex(): Promise<void> {
  const familyId = activeFamilyId.value
  if (familyId === null) {
    resourceIndexData.value = null
    photoRootJsonPathsData.value = []
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    const loadedIndex = await loadResourceIndex(familyId)
    if (activeFamilyId.value === familyId) {
      resourceIndexData.value = loadedIndex
      reconcileActivePhotoDirectory(photoDirectoryTree.value)
      if (props.kind === 'PHOTO') {
        const res = await listPhotoRootJsonFiles(familyId)
        if (activeFamilyId.value === familyId && props.kind === 'PHOTO') photoRootJsonPathsData.value = res
      }
    }
  } catch (error) {
    if (activeFamilyId.value === familyId) errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    isLoading.value = false
  }
}

async function handleChooseDirectories(): Promise<void> {
  if (activeFamilyId.value === null || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  try {
    const tempSelectedRoots = await selectResourceDirectories()
    if (!tempSelectedRoots.length) return
    const tempRoots = [...new Set([...roots.value, ...tempSelectedRoots])]
    await performScan(tempRoots)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  }
}

/** 在当前家庭已扫描目录下创建空子文件夹；成功后更新目录树，不改变照片选择。 */
async function handleCreatePhotoSubfolder(parentPath: string, folderName: string): Promise<boolean> {
  if (props.kind !== 'PHOTO' || activeFamilyId.value === null || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value || !photoDirectoryOptions.value.some((option) => option.path === parentPath)) return false
  const tempFolderName = folderName.trim()
  if (!tempFolderName) return false
  const tempFamilyId = activeFamilyId.value
  isCreatingPhotoSubfolder.value = true
  try {
    const res = await createPhotoSubfolder(tempFamilyId, parentPath, tempFolderName)
    if (activeFamilyId.value !== tempFamilyId) return false
    resourceIndexData.value = res
    ElMessage.success(`子文件夹“${tempFolderName}”已创建`)
    return true
  } catch (error) {
    if (activeFamilyId.value === tempFamilyId) ElMessage.error(error instanceof Error ? error.message : String(error))
    return false
  } finally {
    isCreatingPhotoSubfolder.value = false
  }
}

/** 只从当前家庭的照片扫描目录中移除显式添加的根目录；磁盘文件保持不变。 */
async function handleRemovePhotoDirectory(path: string): Promise<void> {
  if (props.kind !== 'PHOTO' || activeFamilyId.value === null || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isBatchDeleting.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  if (activePhotoDirectory.value !== path || !roots.value.includes(path)) return
  const tempFamilyId = activeFamilyId.value
  try {
    await ElMessageBox.confirm(`仅从照片管理中移除目录“${path}”及其照片索引，不删除本地文件。确定移除吗？`, '移除照片目录', {
      type: 'warning',
      confirmButtonText: '确认移除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (activeFamilyId.value !== tempFamilyId || activePhotoDirectory.value !== path || !roots.value.includes(path) || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isBatchDeleting.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  await performScan(roots.value.filter((tempRoot) => tempRoot !== path))
  if (activeFamilyId.value !== tempFamilyId || roots.value.includes(path)) return
  handleSelectPhotoDirectory(null, false)
  photoDirectoryHistory.value = [null]
  photoHistoryIndex.value = 0
}

/** 二次确认后回收本地子文件夹；只信任当前目录树中的非根节点，最终路径仍由 Rust 校验。 */
async function handleDeletePhotoSubfolder(path: string): Promise<void> {
  if (props.kind !== 'PHOTO' || activeFamilyId.value === null || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isBatchDeleting.value || isDeletingPhotoDirectory.value || isSavingMetadata.value || isBatchMetadataSaving.value || deletingPhotoId.value !== null) return
  if (roots.value.includes(path) || !photoDirectoryOptions.value.some((option) => option.path === path && !option.isRoot)) return
  const tempFamilyId = activeFamilyId.value
  const tempFolderName = path.split(/[\\/]/).filter(Boolean).at(-1) ?? path
  try {
    await ElMessageBox.confirm(`将子文件夹“${tempFolderName}”及其所有内容移入系统回收站？`, '删除照片子文件夹', {
      type: 'warning', confirmButtonText: '移入回收站', cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (activeFamilyId.value !== tempFamilyId || roots.value.includes(path) || isScanning.value || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isBatchDeleting.value || isSavingMetadata.value || isBatchMetadataSaving.value || deletingPhotoId.value !== null) return
  isDeletingPhotoDirectory.value = true
  try {
    const res = await recyclePhotoDirectory(tempFamilyId, path)
    if (activeFamilyId.value !== tempFamilyId) return
    resourceIndexData.value = res
    reconcileActivePhotoDirectory(photoDirectoryTree.value)
    selectedPhotoIds.value = []
    editingResource.value = null
    ElMessage.success(`“${tempFolderName}”已移入回收站`)
  } catch (error) {
    if (activeFamilyId.value === tempFamilyId) ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    isDeletingPhotoDirectory.value = false
  }
}

async function handleRescan(): Promise<void> {
  if (isMovingPhotos.value || isCreatingPhotoSubfolder.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  if (!roots.value.length) {
    await handleChooseDirectories()
    return
  }
  await performScan(roots.value)
}

/** 扫描只重建当前资源类型，其他资源类型的索引由 Rust 层原样保留。 */
async function performScan(scanRoots: string[]): Promise<void> {
  if (activeFamilyId.value === null || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  clearPhotoClickTimer()
  const familyId = activeFamilyId.value
  isScanning.value = true
  errorMessage.value = ''
  try {
    const scannedIndex = await scanResources(familyId, props.kind, scanRoots)
    if (activeFamilyId.value !== familyId) return
    resourceIndexData.value = scannedIndex
    if (props.kind === 'PHOTO') {
      reconcileActivePhotoDirectory(photoDirectoryTree.value)
      const res = await listPhotoRootJsonFiles(familyId)
      if (activeFamilyId.value === familyId && props.kind === 'PHOTO') photoRootJsonPathsData.value = res
    }
    selectedPhotoIds.value = []
    editingResource.value = null
    ElMessage.success(`扫描完成，共找到 ${allResources.value.length} 个文件`)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    isScanning.value = false
  }
}

async function handleOpenResource(resource: ResourceEntry): Promise<void> {
  if (activeFamilyId.value === null) return
  if (props.kind === 'MOVIE') {
    await openWithDefaultApp(activeFamilyId.value, resource.path)
    return
  }
  previewTitle.value = resource.name
  previewUrl.value = ''
  previewHtml.value = ''
  previewText.value = ''
  if (props.kind === 'NOTE') {
    const res = await readTextPreview(activeFamilyId.value, resource.path)
    if (resource.extension === '.txt') previewText.value = res
    else previewHtml.value = DOMPurify.sanitize(await marked.parse(res))
  } else {
    const res = await readBinaryPreview(activeFamilyId.value, resource.path)
    previewUrl.value = `data:${res.mimeType};base64,${res.base64}`
  }
  isPreviewVisible.value = true
}

async function handleReveal(resource: ResourceEntry): Promise<void> {
  if (activeFamilyId.value === null) return
  await revealInExplorer(activeFamilyId.value, resource.path)
}

/** 原图移入回收站后才更新当前家庭的照片索引；失败时保留卡片。 */
async function handleRecyclePhoto(resource: ResourceEntry): Promise<void> {
  if (activeFamilyId.value === null || deletingPhotoId.value !== null || isBatchDeleting.value || isMovingPhotos.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  const tempFamilyId = activeFamilyId.value
  deletingPhotoId.value = resource.id
  try {
    const tempIndex = await recyclePhoto(tempFamilyId, resource.id)
    if (activeFamilyId.value !== tempFamilyId) return
    resourceIndexData.value = tempIndex
    selectedPhotoIds.value = selectedPhotoIds.value.filter((id) => id !== resource.id)
    if (editingResource.value?.id === resource.id) showLastSelectedPhoto()
    ElMessage.success('照片已移入回收站')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    deletingPhotoId.value = null
  }
}

function handleSelectAllVisiblePhotos(): void {
  if (isBatchMetadataSaving.value) return
  const tempVisibleIds = resources.value.map((resource) => resource.id)
  const tempSelectedIds = new Set(selectedPhotoIds.value)
  if (tempVisibleIds.every((id) => tempSelectedIds.has(id))) {
    selectedPhotoIds.value = selectedPhotoIds.value.filter((id) => !tempVisibleIds.includes(id))
    if (editingResource.value && !selectedPhotoIds.value.includes(editingResource.value.id)) showLastSelectedPhoto()
    return
  }
  selectedPhotoIds.value = [...new Set([...selectedPhotoIds.value, ...tempVisibleIds])]
  const tempLastPhoto = resources.value.at(-1)
  if (tempLastPhoto) showPhotoInInspector(tempLastPhoto)
}

function handleClearPhotoSelection(): void {
  if (isBatchMetadataSaving.value) return
  selectedPhotoIds.value = []
  editingResource.value = null
}

/** 逐张保存到原有 JSON；保留原选择，方便继续批量操作和核对结果。 */
async function handleBatchPhotoMetadata(change: PhotoBatchChange): Promise<void> {
  if (props.kind !== 'PHOTO' || activeFamilyId.value === null || !selectedPhotoIds.value.length
    || isBatchMetadataSaving.value || isSavingMetadata.value || isScanning.value || isMovingPhotos.value
    || isBatchDeleting.value || isDeletingPhotoDirectory.value || deletingPhotoId.value !== null) return
  const tempFamilyId = activeFamilyId.value
  const tempPhotoIds = [...selectedPhotoIds.value]
  isBatchMetadataSaving.value = true
  isBatchCategoryMenuVisible.value = false
  isBatchRatingMenuVisible.value = false
  let tempSuccessCount = 0
  let tempFailureCount = 0
  try {
    for (const tempPhotoId of tempPhotoIds) {
      if (activeFamilyId.value !== tempFamilyId || props.kind !== 'PHOTO') break
      const tempResource = resourceIndexData.value?.resources.find((resource) => resource.id === tempPhotoId && resource.kind === 'PHOTO')
      if (!tempResource) {
        tempFailureCount += 1
        continue
      }
      const tempDetails = editablePhotoDetails(tempResource)
      if (change.type === 'category') tempDetails.categories = [...new Set([...tempDetails.categories, change.category])]
      else tempDetails.rating = change.rating
      try {
        const res = await saveResourceMetadata(tempFamilyId, tempPhotoId, { kind: 'PHOTO', details: tempDetails })
        if (activeFamilyId.value !== tempFamilyId || props.kind !== 'PHOTO') break
        resourceIndexData.value = res
        if (editingResource.value?.id === tempPhotoId) showPhotoInInspector(tempResource)
        tempSuccessCount += 1
      } catch {
        tempFailureCount += 1
      }
    }
    if (activeFamilyId.value === tempFamilyId && props.kind === 'PHOTO') {
      if (tempFailureCount) ElMessage.warning(`${tempSuccessCount} 张成功，${tempFailureCount} 张失败，请重试`)
      else ElMessage.success(`${tempSuccessCount} 张照片资料已保存`)
    }
  } finally {
    isBatchMetadataSaving.value = false
  }
}

function onBatchCategoryFocusOut(event: FocusEvent): void {
  const tempNextTarget = event.relatedTarget
  if (tempNextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(tempNextTarget)) return
  isBatchCategoryMenuVisible.value = false
}

function onBatchRatingFocusOut(event: FocusEvent): void {
  const tempNextTarget = event.relatedTarget
  if (tempNextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(tempNextTarget)) return
  isBatchRatingMenuVisible.value = false
}

/** 新分类既可由已有选项选择，也可输入；空白分类不得写入照片资料。 */
async function handleApplyBatchCategory(category: string): Promise<void> {
  const tempCategory = category.trim()
  if (!tempCategory) return
  await handleBatchPhotoMetadata({ type: 'category', category: tempCategory })
}

async function handleApplyBatchRating(rating: number): Promise<void> {
  await handleBatchPhotoMetadata({ type: 'rating', rating })
}

/** 目录树拖拽移动复用桌面端批量移动命令。 */
async function handleMovePhotos(resourceIds: string[], destinationParent: string, newFolderName: string | null): Promise<boolean> {
  if (activeFamilyId.value === null || isMovingPhotos.value || isCreatingPhotoSubfolder.value || isDeletingPhotoDirectory.value || !resourceIds.length || isBatchDeleting.value || isScanning.value || isSavingMetadata.value || isBatchMetadataSaving.value || deletingPhotoId.value !== null) return false
  if (!destinationParent || (newFolderName !== null && !newFolderName.trim())) {
    ElMessage.warning('请选择目标文件夹，或填写新文件夹名称')
    return false
  }
  const familyId = activeFamilyId.value
  const movedCount = resourceIds.length
  isMovingPhotos.value = true
  try {
    const res = await movePhotos(familyId, [...resourceIds], destinationParent, newFolderName)
    if (activeFamilyId.value !== familyId) return false
    resourceIndexData.value = res
    reconcileActivePhotoDirectory(photoDirectoryTree.value)
    selectedPhotoIds.value = []
    editingResource.value = null
    ElMessage.success(`${movedCount} 张照片已移动`)
    return true
  } catch (error) {
    if (activeFamilyId.value === familyId) ElMessage.error(error instanceof Error ? error.message : String(error))
    return false
  } finally {
    isMovingPhotos.value = false
  }
}

/** 逐张调用既有回收站接口；成功一张就同步索引，失败的照片保持选中以便重试。 */
async function handleBatchRecyclePhotos(): Promise<void> {
  if (activeFamilyId.value === null || !selectedPhotoIds.value.length || isBatchDeleting.value || deletingPhotoId.value !== null || isMovingPhotos.value || isDeletingPhotoDirectory.value || isBatchMetadataSaving.value) return
  const tempFamilyId = activeFamilyId.value
  const tempPhotoIds = [...selectedPhotoIds.value]
  try {
    await ElMessageBox.confirm(`将选中的 ${tempPhotoIds.length} 张照片移入系统回收站？`, '批量删除照片', {
      type: 'warning',
      confirmButtonText: '移入回收站',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (activeFamilyId.value !== tempFamilyId || isBatchMetadataSaving.value) return
  isBatchDeleting.value = true
  let tempSuccessCount = 0
  let tempFailureCount = 0
  try {
    for (const tempPhotoId of tempPhotoIds) {
      if (activeFamilyId.value !== tempFamilyId) break
      try {
        const tempIndex = await recyclePhoto(tempFamilyId, tempPhotoId)
        if (activeFamilyId.value !== tempFamilyId) break
        resourceIndexData.value = tempIndex
        selectedPhotoIds.value = selectedPhotoIds.value.filter((id) => id !== tempPhotoId)
        if (editingResource.value?.id === tempPhotoId) showLastSelectedPhoto()
        tempSuccessCount += 1
      } catch {
        tempFailureCount += 1
      }
    }
    if (activeFamilyId.value === tempFamilyId) {
      if (tempFailureCount) ElMessage.warning(`${tempSuccessCount} 张已移入回收站，${tempFailureCount} 张失败，请重试`)
      else ElMessage.success(`${tempSuccessCount} 张照片已移入回收站`)
    }
  } finally {
    isBatchDeleting.value = false
  }
}

watch(photoFilterCategoryOptions, (options) => {
  if (activePhotoCategory.value !== null && !options.includes(activePhotoCategory.value)) handleSelectPhotoCategory(null)
})
watch(activeFamilyId, () => {
  clearPhotoClickTimer()
  draggedPhotoIds.value = []
  isMetadataVisible.value = false
  editingResource.value = null
  resourceIndexData.value = null
  photoRootJsonPathsData.value = []
  selectedPhotoIds.value = []
  activePhotoDirectory.value = null
  activePhotoCategory.value = null
  isPhotoCategoryMenuVisible.value = false
  activePhotoRating.value = null
  isPhotoRatingMenuVisible.value = false
  isBatchCategoryMenuVisible.value = false
  isBatchRatingMenuVisible.value = false
  batchPhotoCategoryInput.value = ''
  photoDirectoryHistory.value = [null]
  photoHistoryIndex.value = 0
  void loadIndex()
})
watch(() => props.kind, () => {
  clearPhotoClickTimer()
  draggedPhotoIds.value = []
  photoRootJsonPathsData.value = []
  isMetadataVisible.value = false
  editingResource.value = null
  activeExtension.value = ''
  keyword.value = ''
  selectedPhotoIds.value = []
  activePhotoDirectory.value = null
  activePhotoCategory.value = null
  isPhotoCategoryMenuVisible.value = false
  activePhotoRating.value = null
  isPhotoRatingMenuVisible.value = false
  isBatchCategoryMenuVisible.value = false
  isBatchRatingMenuVisible.value = false
  batchPhotoCategoryInput.value = ''
  photoDirectoryHistory.value = [null]
  photoHistoryIndex.value = 0
  void loadIndex()
})
watch(photoWallRef, (element) => {
  photoWallObserver?.disconnect()
  photoWallObserver = null
  if (!element) return
  photoWallWidth.value = element.clientWidth || 800
  if (typeof ResizeObserver === 'undefined') return
  photoWallObserver = new ResizeObserver(([entry]) => {
    if (entry && entry.contentRect.width > 0) photoWallWidth.value = entry.contentRect.width
  })
  photoWallObserver.observe(element)
})
onMounted(loadIndex)
onBeforeUnmount(() => {
  clearPhotoClickTimer()
  photoWallObserver?.disconnect()
})
</script>

<template>
  <section class="page-stack prototype-page">
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />

    <div v-if="kind === 'MOVIE' || kind === 'EXAM'" class="stat-grid resource-stats">
      <div class="card stat"><div class="stat-label"><i class="dot"></i>{{ kind === 'MOVIE' ? '收藏影片' : '试卷总数' }}</div><div class="stat-value">{{ allResources.length }}</div><div class="stat-foot">本地已索引文件</div></div>
      <div class="card stat"><div class="stat-label"><i class="dot"></i>{{ kind === 'MOVIE' ? '已看' : '已批改' }}</div><div class="stat-value">{{ allResources.filter((resource) => { const data = metadataFor(resource); return kind === 'MOVIE' ? data?.kind === 'MOVIE' && data.details.status === '已看' : data?.kind === 'EXAM' && data.details.status === '已批改' }).length }}</div><div class="stat-foot">按资料状态统计</div></div>
      <div class="card stat"><div class="stat-label"><i class="dot"></i>{{ kind === 'MOVIE' ? '在看' : '待批改' }}</div><div class="stat-value">{{ allResources.filter((resource) => { const data = metadataFor(resource); return kind === 'MOVIE' ? data?.kind === 'MOVIE' && data.details.status === '在看' : !data || data.kind === 'EXAM' && data.details.status === '待批改' }).length }}</div><div class="stat-foot">按资料状态统计</div></div>
      <div class="card stat"><div class="stat-label"><i class="dot"></i>占用空间</div><div class="stat-value">{{ formatFileSize(allResources.reduce((sum, resource) => sum + resource.size, 0)) }}</div><div class="stat-foot">索引文件合计</div></div>
    </div>

    <div v-if="kind !== 'PHOTO'" class="toolbar resource-toolbar">
      <div class="chips"><button class="chip" :class="{ 'is-active': !activeExtension }" @click="activeExtension = ''">{{ kind === 'NOTE' ? '全部格式' : kind === 'EXAM' ? '全部科目' : '全部' }}</button><button v-for="option in filterOptions" :key="option" class="chip" :class="{ 'is-active': activeExtension === option }" @click="activeExtension = option">{{ kind === 'NOTE' ? option.slice(1).toUpperCase() : option }}</button></div>
      <el-input v-model="keyword" class="search-input" clearable placeholder="搜索文件名或路径" :prefix-icon="Search" />
      <el-button class="toolbar-end" type="primary" :icon="FolderAdd" @click="handleChooseDirectories">{{ kind === 'EXAM' ? '录入试卷目录' : kind === 'MOVIE' ? '添加影片目录' : '添加笔记目录' }}</el-button>
      <el-button :icon="Refresh" :loading="isScanning" @click="handleRescan">重新扫描</el-button>
    </div>

    <div v-loading="isLoading">
      <div v-if="kind === 'PHOTO'" class="photo-library-layout">
        <photo-directory-tree :key="activeFamilyId ?? 'none'" :nodes="photoDirectoryTree" :selected-path="activePhotoDirectory" :total-count="allResources.length" :add-disabled="isMovingPhotos || isCreatingPhotoSubfolder || isDeletingPhotoDirectory || isBatchMetadataSaving" :create-disabled="isScanning || isMovingPhotos || isCreatingPhotoSubfolder || isDeletingPhotoDirectory || isBatchMetadataSaving" :create-subfolder="handleCreatePhotoSubfolder" :removable-paths="roots" :json-root-paths="photoRootJsonPathsData" :remove-disabled="isScanning || isMovingPhotos || isCreatingPhotoSubfolder || isBatchDeleting || isDeletingPhotoDirectory || isBatchMetadataSaving" :dragging-photos="draggedPhotoIds.length > 0" @select="handleSelectPhotoDirectory" @add-directory="handleChooseDirectories" @remove-directory="handleRemovePhotoDirectory" @delete-subfolder="handleDeletePhotoSubfolder" @drop-photos="handleDropPhotos" />
        <div class="card photo-library-main" :style="{ '--photo-thumbnail-size': `${photoThumbnailSize}px` }">
        <div class="photo-view-controls">
          <div class="photo-folder-nav" aria-label="文件夹浏览">
            <button class="photo-top-icon" type="button" title="返回上一个文件夹" aria-label="返回上一个文件夹" :disabled="photoHistoryIndex === 0 || isBatchMetadataSaving" @click="onPhotoHistoryBack"><el-icon><arrow-left /></el-icon></button>
            <button class="photo-top-icon" type="button" title="前进到下一个文件夹" aria-label="前进到下一个文件夹" :disabled="photoHistoryIndex >= photoDirectoryHistory.length - 1 || isBatchMetadataSaving" @click="onPhotoHistoryForward"><el-icon><arrow-right /></el-icon></button>
            <span class="photo-current-directory" :title="activePhotoDirectory ?? '全部照片'">{{ activePhotoDirectoryName }}</span>
          </div>
          <div class="photo-zoom" aria-label="照片缩略图大小">
            <button class="photo-top-icon" type="button" title="缩小照片缩略图" aria-label="缩小照片缩略图" :disabled="photoThumbnailSize <= MIN_PHOTO_THUMBNAIL_SIZE" @click="onPhotoZoomOut"><el-icon><minus /></el-icon></button>
            <input v-model.number="photoThumbnailSize" type="range" :min="MIN_PHOTO_THUMBNAIL_SIZE" :max="MAX_PHOTO_THUMBNAIL_SIZE" :step="PHOTO_THUMBNAIL_STEP" aria-label="调整照片缩略图大小" />
            <button class="photo-top-icon" type="button" title="放大照片缩略图" aria-label="放大照片缩略图" :disabled="photoThumbnailSize >= MAX_PHOTO_THUMBNAIL_SIZE" @click="onPhotoZoomIn"><el-icon><plus /></el-icon></button>
          </div>
          <div class="photo-tools" aria-label="照片操作">
            <button class="photo-tool" :class="{ 'is-active': isPhotoSearchVisible || keyword || activePhotoCategory !== null || activePhotoRating !== null }" type="button" title="搜索照片" aria-label="搜索照片" :aria-expanded="isPhotoSearchVisible" @click="onTogglePhotoSearch"><el-icon><search /></el-icon></button>
            <button class="photo-tool" type="button" title="重新扫描" aria-label="重新扫描" :disabled="isScanning || isMovingPhotos || isDeletingPhotoDirectory || isBatchMetadataSaving" @click="handleRescan"><el-icon><refresh /></el-icon></button>
            <div v-if="isPhotoSearchVisible" class="photo-search-panel" aria-label="搜索和筛选照片">
              <div class="photo-category-control" @focusout="onPhotoCategoryFocusOut" @keydown.esc="isPhotoCategoryMenuVisible = false">
                <button data-testid="photo-category-filter" class="photo-category-trigger" :class="{ 'is-active': activePhotoCategory !== null }" type="button" :title="activePhotoCategory ?? '按分类筛选照片'" :aria-label="activePhotoCategory ? `分类：${activePhotoCategory}` : '按分类筛选照片'" :aria-expanded="isPhotoCategoryMenuVisible" aria-haspopup="listbox" @click="isPhotoCategoryMenuVisible = !isPhotoCategoryMenuVisible"><el-icon><collection-tag /></el-icon><span>{{ activePhotoCategory ?? '分类' }}</span></button>
                <div v-if="isPhotoCategoryMenuVisible" class="photo-category-menu" role="listbox" aria-label="照片分类">
                  <button data-testid="photo-category-all" type="button" role="option" :aria-selected="activePhotoCategory === null" @click="handleSelectPhotoCategory(null)">全部分类</button>
                  <button v-for="category in photoFilterCategoryOptions" :key="category" data-testid="photo-category-option" :data-category="category" type="button" role="option" :aria-selected="activePhotoCategory === category" @click="handleSelectPhotoCategory(category)">{{ category }}</button>
                  <div v-if="!photoFilterCategoryOptions.length" class="photo-category-empty">暂无分类</div>
                </div>
              </div>
              <div class="photo-rating-control" @focusout="onPhotoRatingFocusOut" @keydown.esc="isPhotoRatingMenuVisible = false">
                <button data-testid="photo-rating-filter" class="photo-rating-trigger" :class="{ 'is-active': activePhotoRating !== null }" type="button" :title="activePhotoRating === null ? '按评分筛选照片' : `评分：${activePhotoRating}星`" :aria-label="activePhotoRating === null ? '按评分筛选照片' : `评分：${activePhotoRating}星`" :aria-expanded="isPhotoRatingMenuVisible" aria-haspopup="listbox" @click="isPhotoRatingMenuVisible = !isPhotoRatingMenuVisible"><el-icon><star /></el-icon><span v-if="activePhotoRating !== null">{{ activePhotoRating }}星</span></button>
                <div v-if="isPhotoRatingMenuVisible" class="photo-rating-menu" role="listbox" aria-label="照片评分">
                  <button data-testid="photo-rating-all" type="button" role="option" :aria-selected="activePhotoRating === null" @click="handleSelectPhotoRating(null)">全部评分</button>
                  <button v-for="rating in PHOTO_RATING_OPTIONS" :key="rating" data-testid="photo-rating-option" :data-rating="rating" type="button" role="option" :aria-selected="activePhotoRating === rating" @click="handleSelectPhotoRating(rating)">{{ rating }}星</button>
                </div>
              </div>
              <el-input v-model="keyword" class="photo-search-input" clearable placeholder="搜索文件名或路径" aria-label="搜索文件名或路径" />
            </div>
            <div class="photo-batch-metadata-control" @focusout="onBatchCategoryFocusOut" @keydown.esc="isBatchCategoryMenuVisible = false">
              <button data-testid="batch-category-photos" class="photo-batch-action" type="button" title="批量标记分类" aria-label="批量标记分类" :aria-expanded="isBatchCategoryMenuVisible" :disabled="!selectedPhotoIds.length || isBatchMetadataSaving || isSavingMetadata || isBatchDeleting || isMovingPhotos || isScanning || isDeletingPhotoDirectory || deletingPhotoId !== null" @click="isBatchCategoryMenuVisible = !isBatchCategoryMenuVisible; isBatchRatingMenuVisible = false"><el-icon><collection-tag /></el-icon></button>
              <div v-if="isBatchCategoryMenuVisible" class="photo-batch-metadata-menu photo-batch-category-menu">
                <input v-model="batchPhotoCategoryInput" data-testid="batch-category-input" type="text" placeholder="输入分类后按回车" aria-label="输入要批量添加的分类" @keydown.enter.prevent="handleApplyBatchCategory(batchPhotoCategoryInput)" />
                <button v-for="category in photoFilterCategoryOptions" :key="category" data-testid="batch-category-option" type="button" @click="handleApplyBatchCategory(category)">{{ category }}</button>
              </div>
            </div>
            <div class="photo-batch-metadata-control" @focusout="onBatchRatingFocusOut" @keydown.esc="isBatchRatingMenuVisible = false">
              <button data-testid="batch-rating-photos" class="photo-batch-action" type="button" title="批量打分" aria-label="批量打分" :aria-expanded="isBatchRatingMenuVisible" :disabled="!selectedPhotoIds.length || isBatchMetadataSaving || isSavingMetadata || isBatchDeleting || isMovingPhotos || isScanning || isDeletingPhotoDirectory || deletingPhotoId !== null" @click="isBatchRatingMenuVisible = !isBatchRatingMenuVisible; isBatchCategoryMenuVisible = false"><el-icon><star /></el-icon></button>
              <div v-if="isBatchRatingMenuVisible" class="photo-batch-metadata-menu photo-batch-rating-menu">
                <button v-for="rating in PHOTO_RATING_OPTIONS" :key="rating" data-testid="batch-rating-option" :data-rating="rating" type="button" @click="handleApplyBatchRating(rating)">{{ rating }}星</button>
              </div>
            </div>
            <div class="photo-batch-actions" aria-label="批量操作">
              <button data-testid="select-all-photos" class="photo-batch-action" type="button" :title="resources.length && resources.every((resource) => selectedPhotoIds.includes(resource.id)) ? '取消全选' : '全选当前结果'" :aria-label="resources.length && resources.every((resource) => selectedPhotoIds.includes(resource.id)) ? '取消全选' : '全选当前结果'" :disabled="!resources.length || isBatchDeleting || isMovingPhotos || isBatchMetadataSaving" @click="handleSelectAllVisiblePhotos"><el-icon><check /></el-icon></button>
              <button data-testid="clear-photo-selection" class="photo-batch-action" type="button" :title="`取消选择（${selectedPhotoIds.length}张）`" :aria-label="`取消选择（${selectedPhotoIds.length}张）`" :disabled="!selectedPhotoIds.length || isBatchDeleting || isMovingPhotos || isBatchMetadataSaving" @click="handleClearPhotoSelection"><el-icon><close /></el-icon></button>
              <button data-testid="batch-delete-photos" class="photo-batch-action photo-batch-delete" type="button" title="批量删除" aria-label="批量删除" :disabled="!selectedPhotoIds.length || isBatchDeleting || deletingPhotoId !== null || isMovingPhotos || isBatchMetadataSaving" @click="handleBatchRecyclePhotos"><el-icon><delete /></el-icon></button>
            </div>
          </div>
        </div>
        <div class="card-body">
          <div ref="photoWallRef" class="photo-wall">
          <template v-for="group in photoLayoutGroups" :key="group.month">
            <div class="group-label">{{ group.month }} · {{ group.count }} 张</div>
            <div class="photo-grid">
              <div v-for="(row, rowIndex) in group.rows" :key="rowIndex" class="photo-row" :style="{ '--photo-row-height': `${row.height}px` }">
              <article v-for="{ resource, width } in row.items" :key="resource.id" class="photo-card" :class="{ 'is-selected': selectedPhotoIds.includes(resource.id) }" :style="{ width: `${width}px` }" draggable="true" @dragstart="onPhotoDragStart(resource, $event)" @dragend="onPhotoDragEnd">
                <div class="photo-media">
                  <button class="photo-open" type="button" :aria-label="`选择照片 ${displayTitle(resource)}，Ctrl+单击多选，双击查看大图`" @click="onPhotoClick(resource, $event)" @dblclick="onPhotoDoubleClick(resource)"><local-photo-thumbnail v-if="activeFamilyId !== null" :family-id="activeFamilyId" :resource="resource" /><span class="photo-format">{{ resource.extension.slice(1).toUpperCase() }}</span></button>
                  <span v-if="photoDetails(resource)?.rating" class="photo-rating-badge" :title="`评分：${photoDetails(resource)?.rating}星`" :aria-label="`评分：${photoDetails(resource)?.rating}星`"><el-icon v-for="starIndex in (photoDetails(resource)?.rating ?? 0)" :key="starIndex" class="photo-rating-star" aria-hidden="true"><star-filled /></el-icon></span>
                  <div class="photo-actions">
                    <button class="photo-action photo-action-danger" type="button" title="删除照片" aria-label="删除照片" :disabled="deletingPhotoId === resource.id || isBatchDeleting || isMovingPhotos || isBatchMetadataSaving" @click="handleRecyclePhoto(resource)"><el-icon><delete /></el-icon></button>
                  </div>
                </div>
                <div class="photo-info">
                  <div class="photo-title" :title="displayTitle(resource)">
                    <span class="photo-title-text">{{ displayPhotoTitle(resource) }}</span>
                    <span v-if="photoDetails(resource)?.categories.length" class="photo-categories" :title="photoDetails(resource)?.categories.join('、')"><el-icon><collection-tag /></el-icon><span>{{ photoDetails(resource)?.categories.join('、') }}</span></span>
                  </div>
                  <div class="photo-sub">{{ photoDetails(resource)?.date || resource.modifiedAt.slice(0, 10) }} · {{ formatFileSize(resource.size) }}</div>
                </div>
              </article>
              </div>
            </div>
          </template>
          <div v-if="!resources.length" class="empty"><div class="empty-title">还没有照片</div><div class="empty-desc">添加照片目录并扫描本地文件</div></div>
          </div>
        </div>
        </div>
        <aside class="photo-inspector" aria-label="照片信息">
          <template v-if="selectedPhoto && activeFamilyId !== null">
            <div class="photo-inspector-scroll">
              <div class="photo-inspector-preview"><local-photo-thumbnail :family-id="activeFamilyId" :resource="selectedPhoto" /></div>
              <el-form class="photo-inspector-form" label-position="top" @submit.prevent="persistMetadata">
                <el-form-item class="photo-inspector-field-inline" label="标题" required><el-input v-model="photoForm.title" maxlength="200" /></el-form-item>
                <el-form-item class="photo-inspector-field-inline photo-inspector-description" label="说明"><el-input v-model="photoForm.description" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
                <el-form-item>
                  <div class="photo-inspector-inline-row photo-inspector-category-row">
                    <span class="photo-inspector-current-value photo-current-categories" :title="photoForm.categories.join('、')">{{ photoForm.categories.join('、') || '未分类' }}</span>
                    <el-select v-model="photoForm.categories" multiple filterable allow-create default-first-option placeholder="添加分类">
                      <el-option v-for="category in photoCategoryOptions" :key="category" :label="category" :value="category" />
                    </el-select>
                  </div>
                </el-form-item>
                <el-form-item class="photo-inspector-field-inline" label="评分">
                  <div class="photo-rating"><el-rate :model-value="photoForm.rating ?? 0" :max="5" @change="photoForm.rating = $event" /><button type="button" class="photo-rating-clear" @click="photoForm.rating = null">清除</button></div>
                </el-form-item>
                <el-form-item class="photo-inspector-field-inline" label="拍摄日期"><el-date-picker v-model="photoForm.date" type="date" value-format="YYYY-MM-DD" /></el-form-item>
                <div class="photo-file-info">
                  <div><span>文件名</span><strong :title="selectedPhoto.name">{{ selectedPhoto.name }}</strong></div>
                  <div><span>文件大小</span><strong>{{ formatFileSize(selectedPhoto.size) }}</strong></div>
                  <div><span>修改日期</span><strong>{{ selectedPhoto.modifiedAt.slice(0, 10) }}</strong></div>
                  <div><span>所在目录</span><strong :title="selectedPhoto.path">{{ selectedPhoto.path.replace(/[\\/][^\\/]+$/, '') }}</strong></div>
                </div>
              </el-form>
            </div>
            <div class="photo-inspector-actions">
              <button data-testid="locate-selected-photo" class="photo-locate-button" type="button" @click="handleReveal(selectedPhoto)"><el-icon><folder-opened /></el-icon>定位图片</button>
              <el-button data-testid="save-photo-metadata" class="photo-save-button" type="primary" :loading="isSavingMetadata" :disabled="isMovingPhotos || isBatchMetadataSaving" @click="persistMetadata">保存照片信息</el-button>
            </div>
          </template>
          <div v-else class="photo-inspector-empty">单击照片后，可在这里查看和编辑资料；双击照片查看大图。</div>
        </aside>
      </div>

      <template v-else-if="kind === 'MOVIE'">
        <div class="list-head"><span></span><span>影片</span><span>时长</span><span>状态</span><span>评分</span><span>操作</span></div>
        <div class="movie-list card"><div class="movie-list-inner">
          <div v-for="resource in resources" :key="resource.id" class="movie-row"><div class="poster"><el-icon><film /></el-icon></div><div class="movie-main"><div class="movie-title">{{ displayTitle(resource) }}</div><div class="movie-sub">{{ movieDetails(resource)?.director || '—' }} · {{ movieDetails(resource)?.movieType || resource.extension.slice(1).toUpperCase() }} · {{ movieDetails(resource)?.year || '—' }}</div><div v-if="movieDetails(resource)?.status === '在看'" class="movie-progress">观看进度 {{ movieDetails(resource)?.progress }}%</div></div><div class="movie-size">{{ movieDetails(resource)?.minutes ? movieDetails(resource)?.minutes + ' 分钟' : '—' }}</div><span class="tag tag-primary">{{ movieDetails(resource)?.status || '想看' }}</span><span class="movie-path">{{ movieDetails(resource)?.rating !== null && movieDetails(resource)?.rating !== undefined ? '★ ' + movieDetails(resource)?.rating : '—' }}</span><div class="row-actions"><button class="btn-text" @click="handleOpenResource(resource)">播放</button><button class="btn-text" @click="beginEditMetadata(resource)">编辑</button><button class="btn-text" @click="handleReveal(resource)">定位</button></div></div>
          <div v-if="!resources.length" class="empty"><div class="empty-title">还没有影片</div><div class="empty-desc">添加影片目录并扫描本地文件</div></div>
        </div></div>
      </template>

      <div v-else class="table-wrap"><table><thead><tr><th>{{ kind === 'EXAM' ? '试卷名称' : '笔记标题' }}</th><th>{{ kind === 'EXAM' ? '科目 / 年级' : '文件格式' }}</th><th>{{ kind === 'EXAM' ? '考试日期' : '修改时间' }}</th><th class="right">{{ kind === 'EXAM' ? '分数 / 状态' : '文件大小' }}</th><th class="right">操作</th></tr></thead><tbody>
        <tr v-for="resource in resources" :key="resource.id"><td><div class="cell-title">{{ displayTitle(resource) }}</div><div class="cell-sub" :title="resource.path">{{ resource.path }}</div></td><td><span v-if="kind === 'EXAM' && examDetails(resource)" class="tag tag-primary">{{ examDetails(resource)?.subject }} / {{ examDetails(resource)?.grade }}</span><span v-else class="tag tag-primary">{{ resource.extension.slice(1).toUpperCase() }}</span></td><td class="muted">{{ kind === 'EXAM' ? examDetails(resource)?.date || resource.modifiedAt.slice(0, 10) : resource.modifiedAt.slice(0, 10) }}</td><td class="right muted">{{ kind === 'EXAM' ? (examDetails(resource)?.score === null || examDetails(resource)?.score === undefined ? examDetails(resource)?.status || '待批改' : examDetails(resource)?.score + ' / ' + examDetails(resource)?.fullScore) : formatFileSize(resource.size) }}</td><td class="right"><div class="row-actions"><button class="btn-text" @click="handleOpenResource(resource)">预览</button><button v-if="kind === 'EXAM'" class="btn-text" @click="beginEditMetadata(resource)">编辑</button><button class="btn-text" @click="handleReveal(resource)">定位</button></div></td></tr>
      </tbody></table><div v-if="!resources.length" class="empty"><div class="empty-title">{{ kind === 'EXAM' ? '没有符合条件的试卷' : '没有符合条件的笔记' }}</div><div class="empty-desc">添加本地目录并重新扫描</div></div></div>
    </div>

    <el-alert v-if="resourceIndexData?.warnings.length" :title="`扫描完成，但有 ${resourceIndexData.warnings.length} 个路径无法访问。`" type="warning" show-icon />

    <el-dialog v-if="kind !== 'PHOTO'" v-model="isMetadataVisible" :title="kind === 'MOVIE' ? '编辑影片资料' : '编辑试卷资料'" width="min(620px, 90vw)" destroy-on-close>
      <el-form label-position="top" class="metadata-form" @submit.prevent="persistMetadata">
        <template v-if="kind === 'MOVIE'">
          <el-form-item label="片名" required><el-input v-model="movieForm.title" maxlength="200" /></el-form-item>
          <div class="metadata-row"><el-form-item label="类型"><el-select v-model="movieForm.movieType"><el-option v-for="movieType in movieTypes" :key="movieType" :label="movieType" :value="movieType" /></el-select></el-form-item><el-form-item label="年份"><el-input-number v-model="movieForm.year" :min="1900" :max="2100" :controls="false" /></el-form-item></div>
          <div class="metadata-row"><el-form-item label="导演 / 作者"><el-input v-model="movieForm.director" /></el-form-item><el-form-item label="时长（分钟）"><el-input-number v-model="movieForm.minutes" :min="1" :max="65535" :controls="false" /></el-form-item></div>
          <div class="metadata-row"><el-form-item label="评分（0-10）"><el-input-number v-model="movieForm.rating" :min="0" :max="10" :step="0.1" :controls="false" /></el-form-item><el-form-item label="观看状态"><el-select v-model="movieForm.status"><el-option v-for="status in movieStatuses" :key="status" :label="status" :value="status" /></el-select></el-form-item></div>
          <el-form-item label="观看进度"><el-slider v-model="movieForm.progress" :min="0" :max="100" show-input /></el-form-item>
          <el-form-item label="简介"><el-input v-model="movieForm.description" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        </template>
        <template v-else-if="kind === 'EXAM'">
          <el-form-item label="试卷名称" required><el-input v-model="examForm.title" maxlength="200" /></el-form-item>
          <div class="metadata-row"><el-form-item label="科目"><el-select v-model="examForm.subject"><el-option v-for="subject in subjects" :key="subject" :label="subject" :value="subject" /></el-select></el-form-item><el-form-item label="年级"><el-select v-model="examForm.grade"><el-option v-for="grade in grades" :key="grade" :label="grade" :value="grade" /></el-select></el-form-item></div>
          <div class="metadata-row"><el-form-item label="考试日期"><el-date-picker v-model="examForm.date" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="状态"><el-select v-model="examForm.status"><el-option v-for="status in examStatuses" :key="status" :label="status" :value="status" /></el-select></el-form-item></div>
          <div class="metadata-row"><el-form-item label="满分"><el-input-number v-model="examForm.fullScore" :min="1" :max="65535" :controls="false" /></el-form-item><el-form-item label="得分（未批改可留空）"><el-input-number v-model="examForm.score" :min="0" :max="examForm.fullScore" :controls="false" /></el-form-item></div>
          <el-form-item label="错题与复盘"><el-input v-model="examForm.note" type="textarea" :rows="3" maxlength="2000" /></el-form-item>
        </template>
      </el-form>
      <template #footer><el-button @click="isMetadataVisible = false">取消</el-button><el-button type="primary" :loading="isSavingMetadata" @click="persistMetadata">保存资料</el-button></template>
    </el-dialog>

    <el-dialog v-model="isPreviewVisible" :title="previewTitle" width="min(980px, 86vw)" destroy-on-close>
      <div class="preview-body">
        <img v-if="previewUrl && kind === 'PHOTO'" :src="previewUrl" :alt="previewTitle" />
        <iframe v-else-if="previewUrl" :src="previewUrl" title="试卷预览" />
        <article v-else-if="previewHtml" class="markdown-preview" v-html="previewHtml"></article>
        <pre v-else class="text-preview">{{ previewText }}</pre>
      </div>
    </el-dialog>
  </section>
</template>

<style scoped>
.resource-toolbar { min-height:38px; }.search-input { width:200px; }.resource-stats { margin-bottom:16px; }
.photo-library-layout { display:grid; grid-template-columns:212px minmax(0,1fr) 270px; min-height:100vh; background:var(--bg); }.photo-library-main { position:relative; min-width:0; min-height:0; height:100vh; overflow:auto; border:0; border-radius:0; box-shadow:none; background:var(--bg); }
.photo-library-main::-webkit-scrollbar { width:6px; height:3px; }.photo-inspector-scroll::-webkit-scrollbar { width:4px; height:3px; }.photo-library-main::-webkit-scrollbar-track,.photo-inspector-scroll::-webkit-scrollbar-track { background:transparent; }.photo-library-main::-webkit-scrollbar-thumb,.photo-inspector-scroll::-webkit-scrollbar-thumb { border-radius:3px; background:var(--border); }
.photo-library-layout :deep(.photo-directory-tree) { max-height:none; height:100vh; border:0; border-right:1px solid var(--border); border-radius:0; background:var(--surface); }
.photo-view-controls { position:sticky; top:12px; z-index:3; display:grid; grid-template-columns:minmax(0,1fr) auto minmax(0,1fr); align-items:start; gap:8px; height:40px; margin:12px 12px -52px; pointer-events:none; }
.photo-folder-nav,.photo-zoom,.photo-tools { display:flex; align-items:center; gap:4px; min-width:0; height:40px; padding:3px; border:0; background:transparent; box-shadow:none; pointer-events:auto; }
.photo-folder-nav { justify-self:start; max-width:100%; }.photo-current-directory { min-width:0; max-width:170px; overflow:hidden; padding:0 9px 0 4px; font-size:12px; font-weight:600; text-overflow:ellipsis; white-space:nowrap; }.photo-zoom { justify-self:center; }.photo-zoom input[type='range'] { width:110px; accent-color:var(--primary); cursor:pointer; }.photo-tools { position:relative; justify-self:end; }
.photo-batch-actions { display:flex; align-items:center; gap:4px; margin-left:8px; }.photo-batch-action { display:grid; width:32px; height:32px; flex:none; place-items:center; padding:0; border:0; border-radius:7px; background:transparent; color:var(--text-2); cursor:pointer; font-size:16px; }.photo-batch-action:hover:not(:disabled) { color:var(--primary); background:var(--primary-weak); }.photo-batch-action:disabled { color:var(--text-3); opacity:.58; cursor:default; }.photo-batch-action:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }.photo-batch-delete:not(:disabled) { color:#b64646; }.photo-batch-delete:hover:not(:disabled) { color:#8f2525; background:#fceeee; }
.photo-top-icon { display:grid; width:28px; height:30px; flex:none; place-items:center; padding:0; border:0; border-radius:7px; background:transparent; color:var(--text-2); cursor:pointer; font-size:15px; }.photo-top-icon:hover:not(:disabled) { color:var(--primary); background:var(--primary-weak); }.photo-top-icon:disabled { color:var(--text-3); opacity:.45; cursor:default; }.photo-top-icon:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }
.photo-tool { display:grid; width:32px; height:32px; flex:none; place-items:center; padding:0; border:0; border-radius:7px; background:transparent; color:var(--text-2); cursor:pointer; font-size:16px; }.photo-tool:hover,.photo-tool.is-active { color:var(--primary); background:var(--primary-weak); }.photo-tool:disabled { opacity:.5; cursor:wait; }.photo-tool:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }.photo-search-panel { position:absolute; top:44px; right:0; z-index:6; display:flex; width:min(430px,calc(100vw - 32px)); align-items:center; gap:4px; background:var(--bg); }.photo-search-input { min-width:0; flex:1; }
.photo-category-control { position:relative; flex:none; }.photo-category-trigger { display:flex; max-width:116px; height:32px; align-items:center; gap:5px; padding:0 8px; overflow:hidden; border:0; border-radius:7px; background:transparent; color:var(--text-2); cursor:pointer; font-size:12px; }.photo-category-trigger:hover,.photo-category-trigger.is-active { color:var(--primary); background:var(--primary-weak); }.photo-category-trigger:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }.photo-category-trigger .el-icon { flex:none; font-size:15px; }.photo-category-trigger span { min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.photo-category-menu { position:absolute; top:38px; right:0; z-index:5; width:156px; max-height:220px; overflow:auto; padding:5px; border:1px solid var(--border); border-radius:7px; background:var(--surface); box-shadow:0 5px 18px #00000014; }.photo-category-menu button { display:block; width:100%; min-height:30px; overflow:hidden; padding:5px 8px; border:0; border-radius:4px; background:transparent; color:var(--text-2); cursor:pointer; font-size:12px; text-align:left; text-overflow:ellipsis; white-space:nowrap; }.photo-category-menu button:hover,.photo-category-menu button[aria-selected='true'] { background:var(--primary-weak); color:var(--primary); }.photo-category-empty { padding:7px 8px; color:var(--text-3); font-size:12px; }
.photo-rating-control { position:relative; flex:none; }.photo-rating-trigger { display:flex; min-width:32px; height:32px; align-items:center; justify-content:center; gap:5px; padding:0 8px; border:0; border-radius:7px; background:transparent; color:var(--text-2); cursor:pointer; font-size:12px; white-space:nowrap; }.photo-rating-trigger:hover,.photo-rating-trigger.is-active { color:var(--primary); background:var(--primary-weak); }.photo-rating-trigger:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }.photo-rating-trigger .el-icon { font-size:16px; }.photo-rating-menu { position:absolute; top:38px; right:0; z-index:5; width:116px; padding:5px; border:1px solid var(--border); border-radius:7px; background:var(--surface); box-shadow:0 5px 18px #00000014; }.photo-rating-menu button { display:block; width:100%; min-height:30px; padding:5px 8px; border:0; border-radius:4px; background:transparent; color:var(--text-2); cursor:pointer; font-size:12px; text-align:left; }.photo-rating-menu button:hover,.photo-rating-menu button[aria-selected='true'] { background:var(--primary-weak); color:var(--primary); }
.photo-batch-metadata-control { position:relative; flex:none; }.photo-batch-metadata-menu { position:absolute; top:38px; right:0; z-index:7; width:176px; max-height:240px; overflow:auto; padding:5px; border:1px solid var(--border); border-radius:7px; background:var(--surface); box-shadow:0 5px 18px #00000014; }.photo-batch-metadata-menu input { box-sizing:border-box; width:100%; height:28px; padding:0 8px; border:1px solid var(--border); border-radius:4px; outline:none; background:var(--surface); color:var(--text-2); font-size:12px; }.photo-batch-metadata-menu input:focus { border-color:var(--primary); }.photo-batch-metadata-menu button { display:block; width:100%; min-height:30px; overflow:hidden; padding:5px 8px; border:0; border-radius:4px; background:transparent; color:var(--text-2); cursor:pointer; font-size:12px; text-align:left; text-overflow:ellipsis; white-space:nowrap; }.photo-batch-metadata-menu button:hover { background:var(--primary-weak); color:var(--primary); }.photo-batch-rating-menu { width:104px; }
.photo-library-main .card-body { padding:64px 18px 18px; }.photo-library-main .photo-grid { display:flex; flex-direction:column; gap:17px; }.photo-library-main .photo-row { display:flex; align-items:flex-start; gap:12px; }.photo-library-main .photo-row :deep(.photo-thumb) { height:var(--photo-row-height); aspect-ratio:auto; }.photo-library-main .photo-card { min-width:0; flex:none; overflow:visible; border:0; border-radius:0; background:transparent; box-shadow:none; }.photo-library-main .photo-card:hover { box-shadow:none; }.photo-library-main .photo-card .photo-open { cursor:grab; }.photo-library-main .photo-card .photo-open:active { cursor:grabbing; }.photo-library-main .photo-card.is-selected .photo-open { outline:3px solid var(--primary); outline-offset:2px; }
.photo-library-main .photo-info { padding:7px 0 0; }.photo-library-main .photo-title { gap:5px; font-size:12px; }.photo-library-main .photo-sub { font-size:11px; }.photo-format { position:absolute; right:6px; bottom:6px; padding:1px 4px; border-radius:3px; background:#4b5563b3; color:#fff; font-size:10px; font-weight:500; line-height:15px; text-shadow:0 1px 2px #000b; pointer-events:none; }
.photo-categories { display:flex; max-width:60%; min-width:0; flex:0 1 auto; align-items:center; gap:3px; color:var(--text-3); font-size:11px; font-weight:400; white-space:nowrap; }.photo-categories > span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.photo-categories .el-icon { flex:none; font-size:12px; }.photo-rating-badge { position:absolute; z-index:1; top:6px; left:6px; display:flex; align-items:center; gap:1px; padding:2px 4px; border-radius:3px; background:#4b5563b3; color:#ffd166; pointer-events:none; }.photo-rating-star { font-size:12px; }
.group-label { display:flex; align-items:center; gap:8px; margin:4px 0 12px; color:var(--text-2); font-size:13px; font-weight:600; }.group-label:not(:first-child) { margin-top:22px; }.group-label::after { content:''; flex:1; height:1px; background:var(--border); }
.photo-media { position:relative; }.photo-open { position:relative; display:block; width:100%; padding:0; overflow:hidden; border:0; border-radius:7px; background:transparent; cursor:pointer; }.photo-title { display:flex; align-items:center; justify-content:space-between; gap:6px; min-width:0; }.photo-title-text { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.photo-actions { position:absolute; z-index:1; top:8px; right:8px; display:flex; gap:4px; opacity:0; visibility:hidden; pointer-events:none; transition:opacity .15s; }.photo-media:hover .photo-actions, .photo-media:focus-within .photo-actions { opacity:1; visibility:visible; pointer-events:auto; }.photo-action { display:grid; width:27px; height:27px; place-items:center; padding:0; border:1px solid var(--border); border-radius:7px; background:#ffffffed; color:var(--text-2); cursor:pointer; }.photo-action:hover { color:var(--primary); }.photo-action-danger:hover { color:#e0484b; }.photo-action:disabled { opacity:.55; cursor:wait; }
.photo-inspector { display:flex; min-width:0; height:100vh; flex-direction:column; overflow:hidden; padding:18px 16px; border-left:1px solid var(--border); background:var(--surface); }.photo-inspector-scroll { min-height:0; flex:1; overflow:auto; padding-right:8px; }.photo-inspector-actions { display:flex; flex:none; flex-direction:column; gap:8px; padding-top:12px; border-top:1px solid var(--border); background:var(--surface); box-shadow:0 -8px 12px -12px #0003; }.photo-inspector-preview { overflow:hidden; border-radius:8px; }.photo-inspector-preview :deep(.photo-thumb) { aspect-ratio:auto; }.photo-inspector-preview :deep(.photo-thumb img) { width:auto; height:auto; max-width:100%; max-height:350px; }.photo-inspector-form { margin-top:18px; }.photo-inspector-form :deep(.el-form-item) { margin-bottom:14px; }.photo-inspector-form :deep(.el-form-item__label) { padding-bottom:4px; color:var(--text-3); font-size:12px; }.photo-inspector-form :deep(.el-select),.photo-inspector-form :deep(.el-date-editor) { width:100%; }.photo-inspector-empty { padding-top:10px; color:var(--text-3); font-size:12px; line-height:1.8; }.photo-rating { display:flex; align-items:center; gap:12px; }.photo-rating-clear { padding:0; border:0; background:none; color:var(--text-3); cursor:pointer; font-size:12px; }.photo-file-info { padding-top:14px; border-top:1px solid var(--border); }.photo-file-info div { display:flex; justify-content:space-between; gap:10px; margin:7px 0; font-size:11px; }.photo-file-info span { flex:none; color:var(--text-3); }.photo-file-info strong { overflow:hidden; color:var(--text-2); font-weight:500; text-align:right; text-overflow:ellipsis; white-space:nowrap; }.photo-save-button { width:100%; margin:0; }
.photo-inspector-inline-row { display:flex; width:100%; align-items:center; gap:4px; }
.photo-inspector-form :deep(.photo-inspector-field-inline.el-form-item--label-top) { display:flex; align-items:center; gap:4px; }
.photo-inspector-form :deep(.photo-inspector-field-inline .el-form-item__label) { display:flex; width:56px; height:28px; flex:none; align-items:center; margin:0; padding:0; line-height:1.2; }
.photo-inspector-form :deep(.photo-inspector-field-inline .el-form-item__content) { min-width:0; flex:1; }
.photo-inspector-form :deep(.photo-inspector-description.el-form-item--label-top) { align-items:flex-start; }
.photo-inspector-form :deep(.el-input) { --el-input-height:28px; }
.photo-inspector-form :deep(.el-input__wrapper),.photo-inspector-form :deep(.el-select__wrapper) { height:28px; min-height:28px; max-height:28px; border-radius:4px; }
.photo-inspector-form :deep(.el-select__wrapper) { overflow:hidden; padding:2px 8px; }
.photo-inspector-form :deep(.el-textarea__inner) { height:56px !important; min-height:56px !important; max-height:56px; overflow-y:auto; padding:4px 8px; border-radius:4px; resize:none; }
.photo-inspector-field-inline .photo-rating { gap:4px; min-width:0; }
.photo-inspector-field-inline .photo-rating :deep(.el-rate) { --el-rate-icon-size:16px; --el-rate-icon-margin:2px; }
.photo-locate-button { display:flex; width:100%; height:28px; align-items:center; justify-content:center; gap:6px; border:1px solid var(--border); border-radius:4px; background:var(--surface); color:var(--text-2); cursor:pointer; font-size:12px; }.photo-locate-button:hover { border-color:var(--primary); color:var(--primary); }
.photo-inspector-current-value { flex:0 0 56px; min-width:0; overflow:hidden; color:var(--text-2); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.photo-inspector-category-row :deep(.el-select) { flex:1; min-width:0; width:0; }
.list-head { display:grid; grid-template-columns:44px minmax(170px,1fr) 72px 60px minmax(120px,.5fr) 100px; gap:12px; padding:8px 16px; border:1px solid var(--border); border-bottom:0; border-radius:12px 12px 0 0; background:var(--surface-weak); color:var(--text-3); font-size:12px; }.movie-list { border-radius:0 0 12px 12px !important; box-shadow:none !important; }.movie-list-inner { padding:0 16px; }.movie-row { display:grid !important; grid-template-columns:44px minmax(170px,1fr) 72px 60px minmax(120px,.5fr) 100px; gap:12px !important; padding:10px 0 !important; }.poster { width:44px !important; height:60px !important; }.poster .el-icon { font-size:22px; }.movie-size { color:var(--text-2); font-size:12px; }.movie-path { overflow:hidden; color:var(--text-3); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.table-wrap th { padding:8px 16px !important; }.table-wrap td { padding:9px 16px !important; }
.cell-sub { max-width:360px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.preview-body { display: grid; max-height: 70vh; overflow: auto; place-items: center; }.preview-body img { max-width: 100%; max-height: 68vh; object-fit: contain; }.preview-body iframe { width: 100%; height: 68vh; border: 0; }.markdown-preview, .text-preview { width: 100%; margin: 0; padding: 12px 24px; color: #25342c; line-height: 1.75; white-space: pre-wrap; }.markdown-preview :deep(img) { max-width: 100%; }
.metadata-row { display:grid; grid-template-columns:1fr 1fr; gap:16px; }.metadata-row .el-form-item { min-width:0; }.metadata-form :deep(.el-select), .metadata-form :deep(.el-date-editor), .metadata-form :deep(.el-input-number) { width:100%; }.movie-progress { color:var(--primary); font-size:12px; }
@media (max-width:1100px) { .photo-library-layout { grid-template-columns:180px minmax(0,1fr) 230px; } .photo-zoom input[type='range'] { width:72px; } .photo-current-directory { max-width:90px; } }
</style>
