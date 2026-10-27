<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Collection, Document, Star, Select as ChecklistIcon, Box, Search, EditPen, Plus } from '@element-plus/icons-vue'
import QuickNoteCard from '@/views/quick-notes/QuickNoteCard.vue'
import QuickNoteEditor from '@/views/quick-notes/QuickNoteEditor.vue'
import { useQuickNotes } from '@/views/quick-notes/useQuickNotes'

type NoteSection = 'all' | 'pinned' | 'tasks' | 'archive'
type NoteFilter = 'all' | 'text' | 'image' | 'task'

const {
  notesData, isLoading, isLoadFailed, isSaving, isUploading, busyNoteId, isEditorOpen, editingNote, draft,
  familyId, canCreate, isDraftDirty, isReadOnly, canWrite, apiLoadNotes, handleOpenEditor, handleCloseEditor,
  handleSaveEditor, handleUpdateNote, handleRemoveNote, handleUploadImages,
} = useQuickNotes()
const section = ref<NoteSection>('all')
const filter = ref<NoteFilter>('all')
const keyword = ref('')
const selectedTag = ref('')
const editorRef = ref<InstanceType<typeof QuickNoteEditor> | null>(null)
const options = {
  sections: [
    { key: 'all', label: '全部笔记', icon: Document }, { key: 'pinned', label: '已置顶', icon: Star },
    { key: 'tasks', label: '待办清单', icon: ChecklistIcon }, { key: 'archive', label: '归档', icon: Box },
  ],
  filters: [{ key: 'all', label: '全部' }, { key: 'text', label: '文字' }, { key: 'image', label: '图片' }, { key: 'task', label: '待办' }],
} as const

const activeNotes = computed(() => notesData.value.filter((note) => note.status === 'ACTIVE'))
const sectionCounts = computed(() => ({
  all: activeNotes.value.length, pinned: activeNotes.value.filter((note) => note.pinned).length,
  tasks: activeNotes.value.filter((note) => note.tasks.length).length,
  archive: notesData.value.filter((note) => note.status === 'ARCHIVED').length,
}))
const tags = computed(() => [...new Set(notesData.value.flatMap((note) => note.tags))].sort((a, b) => a.localeCompare(b, 'zh-CN')))
const visibleNotes = computed(() => notesData.value.filter((note) => {
  if (section.value === 'archive' && note.status !== 'ARCHIVED') return false
  if (section.value !== 'archive' && note.status !== 'ACTIVE') return false
  if (section.value === 'pinned' && !note.pinned) return false
  if (section.value === 'tasks' && !note.tasks.length) return false
  if (selectedTag.value && !note.tags.includes(selectedTag.value)) return false
  if (filter.value === 'image' && !note.imageIds.length) return false
  if (filter.value === 'task' && !note.tasks.length) return false
  if (filter.value === 'text' && (!note.body && !note.title || note.imageIds.length || note.tasks.length)) return false
  const tempKeyword = keyword.value.trim().toLocaleLowerCase()
  return !tempKeyword || [note.title, note.body, ...note.tags, ...note.tasks.map((task) => task.text)].some((value) => value.toLocaleLowerCase().includes(tempKeyword))
}).sort((a, b) => {
  const tempOrder = a.updatedAt.localeCompare(b.updatedAt) || a.id - b.id
  return -tempOrder
}))
const noteGroups = computed(() => {
  if (section.value === 'all') return [
    { label: '置顶', notes: visibleNotes.value.filter((note) => note.pinned) },
    { label: '全部笔记', notes: visibleNotes.value.filter((note) => !note.pinned) },
  ].filter((group) => group.notes.length)
  const tempTitles: Record<NoteSection, string> = { all: '全部笔记', pinned: '已置顶', tasks: '待办清单', archive: '归档' }
  return [{ label: tempTitles[section.value], notes: visibleNotes.value }].filter((group) => group.notes.length)
})
const hasFilters = computed(() => Boolean(keyword.value || selectedTag.value || filter.value !== 'all'))
const emptyTitle = computed(() => {
  if (hasFilters.value) return '没有找到匹配的笔记'
  const tempTitles: Record<NoteSection, string> = {
    all: '从一条小记录开始', pinned: '还没有置顶笔记', tasks: '还没有待办清单',
    archive: '还没有归档笔记',
  }
  return tempTitles[section.value]
})

watch(familyId, () => {
  keyword.value = ''; selectedTag.value = ''; section.value = 'all'; filter.value = 'all'
})

function onSelectSection(value: NoteSection): void {
  section.value = value
  selectedTag.value = ''
  filter.value = 'all'
}
function onSelectTag(tag: string): void {
  selectedTag.value = selectedTag.value === tag ? '' : tag
  section.value = 'all'
}
function onClearFilters(): void {
  keyword.value = ''; selectedTag.value = ''; filter.value = 'all'
}
async function onQuickAction(type: 'image' | 'task' | 'tag'): Promise<void> {
  if (!await handleOpenEditor()) return
  if (type === 'task' && !draft.value.tasks.length) draft.value.tasks.push({ text: '', done: false })
  await nextTick()
  editorRef.value?.handleQuickAction(type)
}
function isNoteBusy(): boolean {
  return isSaving.value || isUploading.value || busyNoteId.value !== null || isLoading.value
}

onBeforeRouteLeave(async () => {
  if (isSaving.value || isUploading.value) return false
  if (!isDraftDirty.value) return true
  try {
    await ElMessageBox.confirm('还有未保存的内容，确定离开随身记吗？', '离开页面', { confirmButtonText: '离开', cancelButtonText: '继续记录', type: 'warning' })
    return true
  } catch { return false }
})
</script>

<template>
  <div class="quick-notes-page">
    <aside class="notes-sidebar" aria-label="笔记分类">
      <div class="notes-brand"><collection /><strong>随身记</strong></div>
      <nav class="note-categories">
        <button v-for="item in options.sections" :key="item.key" type="button" :class="{ active: section === item.key && !selectedTag }" :aria-current="section === item.key && !selectedTag ? 'page' : undefined" @click="onSelectSection(item.key)"><component :is="item.icon" /><span>{{ item.label }}</span><small>{{ sectionCounts[item.key] }}</small></button>
      </nav>
      <div class="tag-heading"><span>标签</span><button type="button" aria-label="新建带标签的笔记" :disabled="!canCreate" @click="onQuickAction('tag')"><plus /></button></div>
      <div class="tag-list">
        <button v-for="tag in tags" :key="tag" type="button" :class="{ active: selectedTag === tag }" @click="onSelectTag(tag)"><span class="hash">#</span><span>{{ tag }}</span></button>
        <p v-if="!tags.length" class="no-tags">给笔记添加标签，<br />在这里快速找到它。</p>
      </div>
    </aside>

    <section class="notes-main" :aria-busy="isLoading">
      <header class="notes-header">
        <div v-if="canCreate" class="filter-tabs" role="group" aria-label="笔记类型"><button v-for="item in options.filters" :key="item.key" type="button" :class="{ active: filter === item.key }" :aria-pressed="filter === item.key" @click="filter = item.key">{{ item.label }}</button></div>
        <div class="header-tools">
          <label class="search-box"><search /><input v-model="keyword" class="search-input" type="search" placeholder="搜索笔记…" aria-label="搜索笔记" /></label>
          <button type="button" class="new-note-button primary-button" :disabled="!canCreate || isNoteBusy()" @click="handleOpenEditor()"><plus />新建</button>
        </div>
      </header>

      <div v-if="!canCreate" class="empty-state"><collection /><h2>请先选择家庭</h2><p>选择家庭后，即可记录和查看随身记。</p></div>
      <template v-else>
        <div v-if="selectedTag" class="selected-tag"># {{ selectedTag }}<button type="button" aria-label="清除标签筛选" @click="selectedTag = ''">×</button></div>
        <div v-if="isLoading" class="loading-state" role="status">正在读取笔记…</div>
        <div v-else-if="isLoadFailed" class="empty-state"><h2>笔记未能加载</h2><p>请检查连接后重试。</p><button type="button" class="primary-button" @click="apiLoadNotes">重新加载</button></div>
        <div v-else-if="!visibleNotes.length" class="empty-state"><edit-pen /><h2>{{ emptyTitle }}</h2><p>{{ hasFilters ? '换个关键词，或清除筛选再看看。' : '灵感、日常、待办，都可以留在这里。' }}</p><button v-if="hasFilters" type="button" class="secondary-button" @click="onClearFilters">清除筛选</button></div>
        <template v-else>
          <section v-for="group in noteGroups" :key="group.label" class="note-group"><h2 v-if="group.label !== '全部笔记'">{{ group.label }}<span>{{ group.notes.length }}</span></h2><div class="notes-grid"><quick-note-card v-for="note in group.notes" :key="`${familyId}:${note.id}`" :note="note" :can-write="canWrite(note)" :is-busy="isNoteBusy()" :is-selected="isEditorOpen && editingNote?.id === note.id" @open="handleOpenEditor(note)" @update="handleUpdateNote(note, $event)" @remove="handleRemoveNote(note)" /></div></section>
        </template>
      </template>
    </section>

    <quick-note-editor v-if="isEditorOpen" :key="`${familyId}:${editingNote?.id ?? 'new'}`" ref="editorRef" v-model="draft" :is-new="!editingNote" :is-read-only="isReadOnly" :is-saving="isSaving" :is-uploading="isUploading" @save="handleSaveEditor" @cancel="handleCloseEditor" @upload="handleUploadImages" />
  </div>
</template>

<style scoped>
.quick-notes-page { display:flex; height:100vh; overflow:hidden; color:#25322a; background:#f5f6f8; font-family:inherit; }
button,input,textarea,select { font:inherit; }button { cursor:pointer; }button:disabled { cursor:default; opacity:.5; }button:focus-visible,input:focus-visible,textarea:focus-visible,select:focus-visible { outline:2px solid #236b4e; outline-offset:3px; }svg { width:19px; height:19px; flex-shrink:0; }
.notes-sidebar { width:186px; flex-shrink:0; padding:25px 14px 18px; background:#fff; border-right:1px solid #e9edea; display:flex; flex-direction:column; box-sizing:border-box; }.notes-brand { display:flex; align-items:center; gap:11px; padding:0 12px 28px; color:#1c2821; }.notes-brand svg { width:23px; height:23px; }.notes-brand strong { font-size:20px; font-weight:650; }
.note-categories { display:grid; gap:5px; }.note-categories button { display:flex; align-items:center; gap:10px; width:100%; min-height:43px; padding:10px 12px; border:0; border-radius:9px; background:none; color:#5c6860; text-align:left; font-size:13px; }.note-categories button:hover,.tag-list button:hover { background:#f4f7f4; }.note-categories button.active { background:#eaf3ee; color:#236b4e; }.note-categories small { margin-left:auto; color:#89978e; font-size:11px; }
.tag-heading { display:flex; align-items:center; justify-content:space-between; margin:25px 10px 10px; padding-top:22px; border-top:1px solid #edf0ee; color:#8b978e; font-size:12px; }.tag-heading button { display:grid; place-items:center; width:24px; height:24px; border:0; background:none; color:#8b978e; }.tag-list { min-height:0; flex:1; overflow-y:auto; }.tag-list button { display:flex; align-items:center; gap:12px; width:100%; border:0; background:none; color:#58685e; border-radius:7px; padding:11px 12px; text-align:left; font-size:13px; overflow-wrap:anywhere; }.tag-list button.active { color:#236b4e; background:#edf5f0; }.hash { color:#87938a; font-size:20px; line-height:1; }.no-tags { color:#a0aaa3; font-size:12px; line-height:1.8; padding:0 12px; }
.notes-main { flex:1; min-width:0; overflow-y:auto; padding:27px 28px 40px; }
.notes-header { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:16px; margin-bottom:20px; }
.header-tools { display:flex; align-items:center; gap:12px; margin-left:auto; }
.search-box { display:flex; align-items:center; gap:8px; padding:9px 12px; border:1px solid #e0e6e1; border-radius:9px; color:#89958d; background:#fff8; }
.search-box svg { width:16px; height:16px; }
.search-input { width:170px; padding:0; outline:none; border:0; background:none; color:#3d4b41; font-size:12px; }
.primary-button,.secondary-button { padding:9px 19px; border:0; border-radius:7px; background:#236b4e; color:#fff; font-size:12px; }
.primary-button:hover:not(:disabled) { background:#185b40; }
.secondary-button { background:#eaf1ec; color:#4d705a; }
.new-note-button { display:flex; align-items:center; justify-content:center; gap:6px; min-height:36px; white-space:nowrap; }
.new-note-button svg { width:16px; height:16px; }
.filter-tabs { display:flex; gap:9px; }.filter-tabs button { border:1px solid #e0e6e1; border-radius:20px; padding:5px 13px; color:#859087; background:transparent; font-size:12px; }.filter-tabs button.active { background:#236b4e; color:#fff; border-color:#236b4e; }.note-group { margin-bottom:23px; }.note-group h2 { display:flex; align-items:center; gap:8px; margin:0 0 12px; font-size:13px; color:#526158; font-weight:600; }.note-group h2 span { color:#9ea79f; font-size:11px; font-weight:400; }
.notes-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:16px; }
.selected-tag { display:inline-flex; align-items:center; gap:8px; padding:4px 10px; border-radius:15px; background:#e3eee7; color:#397453; font-size:12px; margin-bottom:16px; }.selected-tag button { border:0; background:none; color:inherit; }
.empty-state { display:flex; flex-direction:column; align-items:center; justify-content:center; padding:75px 15px; text-align:center; color:#929e95; }.empty-state > svg { width:36px; height:36px; color:#9fb6a6; margin-bottom:12px; }.empty-state h2 { color:#5b6e60; font-size:16px; font-weight:500; margin:0 0 10px; }.empty-state p { font-size:12px; line-height:1.9; margin:0 0 18px; }.loading-state { color:#8a9b8f; text-align:center; padding:80px 0; font-size:13px; }.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
@media(min-width:1650px) { .notes-main { padding-left:36px; padding-right:36px; } }
@media(max-width:1250px) { .notes-sidebar { width:166px; padding-inline:10px; }.notes-main { padding:24px 20px; }.quick-notes-page:has(.note-editor) .notes-grid { grid-template-columns:minmax(0,1fr); } }
@media(max-width:850px) { .notes-sidebar { width:150px; }.notes-main { padding:22px 16px; }.notes-grid { grid-template-columns:minmax(0,1fr); }.header-tools { width:100%; }.search-box { flex:1; min-width:0; }.search-input { width:100%; } }
@media(prefers-reduced-motion:reduce) { * { transition:none !important; } }
</style>
