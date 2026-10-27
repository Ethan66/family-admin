import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { quickNotesApi } from '@/api/quickNotes'
import { useAuthStore } from '@/stores/auth'
import type { QuickNote, QuickNotePayload } from '@/types/quickNote'
import { createNoteDraft } from './model'

/** 家庭切换时丢弃旧响应和编辑副本，异步完成后不允许旧家庭数据重新进入页面。 */
export function useQuickNotes() {
  const authStore = useAuthStore()
  const notesData = ref<QuickNote[]>([])
  const isLoading = ref(false)
  const isLoadFailed = ref(false)
  const isSaving = ref(false)
  const isUploading = ref(false)
  const busyNoteId = ref<number | null>(null)
  const editingNote = ref<QuickNote | null>(null)
  const draft = ref<QuickNotePayload>(createNoteDraft())
  const initialDraft = ref(JSON.stringify(draft.value))
  let scopeVersion = 0
  let editorVersion = 0

  const familyId = computed(() => authStore.isSuperAdmin ? authStore.selectedFamilyId : authStore.user?.familyId)
  const canCreate = computed(() => Boolean(familyId.value))
  const isEditorOpen = computed(() => canCreate.value)
  const isDraftDirty = computed(() => isEditorOpen.value && JSON.stringify(draft.value) !== initialDraft.value)
  const isReadOnly = computed(() => editingNote.value !== null && !canWrite(editingNote.value))

  watch(familyId, () => {
    scopeVersion++
    notesData.value = []
    resetEditor()
    busyNoteId.value = null
    isSaving.value = false
    isUploading.value = false
    isLoading.value = false
    isLoadFailed.value = false
    if (canCreate.value) void apiLoadNotes()
  }, { immediate: true, flush: 'sync' })

  function canWrite(note: QuickNote): boolean {
    return authStore.isSuperAdmin || note.createdBy === authStore.user?.id
  }

  // 面板始终保留；清空时连同原稿快照一起重置，避免把空白新建状态误判为未保存。
  function resetEditor(): void {
    editorVersion++
    editingNote.value = null
    draft.value = createNoteDraft()
    initialDraft.value = JSON.stringify(draft.value)
  }

  async function apiLoadNotes(): Promise<void> {
    if (!canCreate.value || isSaving.value || isUploading.value || busyNoteId.value !== null) return
    const tempScope = ++scopeVersion
    isLoading.value = true
    isLoadFailed.value = false
    try {
      const res = await quickNotesApi.apiList()
      if (tempScope === scopeVersion) notesData.value = res
    } catch {
      // 请求层提示具体错误；本页单独保留失败状态，提供明确的重试入口。
      if (tempScope === scopeVersion) isLoadFailed.value = true
    } finally {
      if (tempScope === scopeVersion) isLoading.value = false
    }
  }

  function applySavedNote(note: QuickNote): void {
    const tempIndex = notesData.value.findIndex((item) => item.id === note.id)
    if (tempIndex === -1) notesData.value.unshift(note)
    else notesData.value.splice(tempIndex, 1, note)
  }

  async function confirmDiscard(): Promise<boolean> {
    if (!isDraftDirty.value) return true
    try {
      await ElMessageBox.confirm('当前修改尚未保存，确定放弃吗？', '放弃修改', { confirmButtonText: '放弃', cancelButtonText: '继续编辑', type: 'warning' })
      return true
    } catch { return false }
  }

  async function handleOpenEditor(note?: QuickNote): Promise<boolean> {
    if (!canCreate.value || isLoading.value || isSaving.value || isUploading.value || busyNoteId.value !== null) return false
    const tempScope = scopeVersion
    if (!await confirmDiscard() || tempScope !== scopeVersion) return false
    editorVersion++
    editingNote.value = note ?? null
    draft.value = createNoteDraft(note)
    initialDraft.value = JSON.stringify(draft.value)
    return true
  }

  async function handleCloseEditor(): Promise<void> {
    if (isSaving.value || isUploading.value) return
    const tempScope = scopeVersion
    if (!await confirmDiscard() || tempScope !== scopeVersion) return
    resetEditor()
  }

  function validPayload(payload: QuickNotePayload): boolean {
    if (!payload.title.trim() && !payload.body.trim() && !payload.imageIds.length && !payload.tasks.some((task) => task.text.trim())) {
      ElMessage.warning('请记录一些内容后再保存')
      return false
    }
    if (payload.tags.length > 20 || payload.tags.some((tag) => tag.length > 30)) {
      ElMessage.warning('最多添加 20 个标签，每个标签不超过 30 字')
      return false
    }
    return true
  }

  async function handleSaveEditor(): Promise<void> {
    if (isReadOnly.value || isSaving.value || isUploading.value || !canCreate.value) return
    const tempPayload = createNoteDraft()
    Object.assign(tempPayload, draft.value, {
      title: draft.value.title.trim(), body: draft.value.body.trim(),
      tags: [...new Set(draft.value.tags.map((tag) => tag.trim()).filter(Boolean))],
      tasks: draft.value.tasks.filter((task) => task.text.trim()).map((task) => ({ ...task, text: task.text.trim() })),
    })
    if (!validPayload(tempPayload)) return
    const tempScope = scopeVersion
    isSaving.value = true
    try {
      const res = editingNote.value
        ? await quickNotesApi.apiUpdate(editingNote.value.id, tempPayload)
        : await quickNotesApi.apiCreate(tempPayload)
      if (tempScope !== scopeVersion) return
      applySavedNote(res)
      resetEditor()
      ElMessage.success('笔记已保存')
    } finally { if (tempScope === scopeVersion) isSaving.value = false }
  }

  async function handleUpdateNote(note: QuickNote, changes: Partial<QuickNotePayload>): Promise<void> {
    if (!canWrite(note) || busyNoteId.value !== null || isSaving.value || isUploading.value) return
    const tempScope = scopeVersion
    if (editingNote.value?.id === note.id && !await confirmDiscard()) return
    if (tempScope !== scopeVersion) return
    busyNoteId.value = note.id
    try {
      const res = await quickNotesApi.apiUpdate(note.id, { ...createNoteDraft(note), ...changes })
      if (tempScope !== scopeVersion) return
      applySavedNote(res)
      if (editingNote.value?.id === note.id) {
        draft.value = createNoteDraft(res)
        initialDraft.value = JSON.stringify(draft.value)
        editingNote.value = res
      }
    } finally { if (tempScope === scopeVersion) busyNoteId.value = null }
  }

  async function handleRemoveNote(note: QuickNote): Promise<void> {
    if (!canWrite(note) || busyNoteId.value !== null || isSaving.value || isUploading.value) return
    const tempScope = scopeVersion
    try {
      await ElMessageBox.confirm('删除后无法恢复，确定删除这条笔记吗？', '删除笔记', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' })
    } catch { return }
    if (tempScope !== scopeVersion) return
    busyNoteId.value = note.id
    try {
      await quickNotesApi.apiRemove(note.id, note.version)
      if (tempScope !== scopeVersion) return
      notesData.value = notesData.value.filter((item) => item.id !== note.id)
      if (editingNote.value?.id === note.id) resetEditor()
    } finally { if (tempScope === scopeVersion) busyNoteId.value = null }
  }

  async function handleUploadImages(files: FileList): Promise<void> {
    if (isUploading.value || isSaving.value || isReadOnly.value || !canCreate.value) return
    const tempFiles = Array.from(files)
    if (draft.value.imageIds.length + tempFiles.length > 9) { ElMessage.warning('每条笔记最多添加 9 张图片'); return }
    if (tempFiles.some((file) => !['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type) || file.size > 10 * 1024 * 1024)) {
      ElMessage.warning('请选择不超过 10MB 的 JPG、PNG、WebP 或 GIF 图片'); return
    }
    const tempScope = scopeVersion
    const tempEditor = editorVersion
    isUploading.value = true
    try {
      for (const tempFile of tempFiles) {
        const res = await quickNotesApi.apiUpload(tempFile)
        if (tempScope !== scopeVersion || tempEditor !== editorVersion) return
        draft.value.imageIds.push(res.id)
      }
    } finally { if (tempScope === scopeVersion && tempEditor === editorVersion) isUploading.value = false }
  }

  onBeforeUnmount(() => { scopeVersion++; editorVersion++ })

  return { notesData, isLoading, isLoadFailed, isSaving, isUploading, busyNoteId, isEditorOpen, editingNote, draft,
    familyId, canCreate, isDraftDirty, isReadOnly, canWrite, apiLoadNotes, handleOpenEditor, handleCloseEditor,
    handleSaveEditor, handleUpdateNote, handleRemoveNote, handleUploadImages }
}
