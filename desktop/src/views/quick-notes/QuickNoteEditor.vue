<script setup lang="ts">
import { computed, ref } from 'vue'
import { Close, Picture as PictureIcon, Plus, Select as ChecklistIcon, Star, StarFilled, Delete } from '@element-plus/icons-vue'
import NoteImage from '@/components/NoteImage.vue'
import type { QuickNotePayload } from '@/types/quickNote'

const props = defineProps<{ modelValue: QuickNotePayload; isNew: boolean; isReadOnly: boolean; isSaving: boolean; isUploading: boolean }>()
const emit = defineEmits<{
  'update:modelValue': [value: QuickNotePayload]
  save: []
  cancel: []
  upload: [files: FileList]
}>()
const fileInputRef = ref<HTMLInputElement | null>(null)
const tagsInputRef = ref<HTMLInputElement | null>(null)
const isLocked = computed(() => props.isReadOnly || props.isSaving || props.isUploading)
const tagsText = computed({
  get: () => props.modelValue.tags.join('，'),
  set: (value: string) => handleChange({ tags: value.split(/[,，]/) }),
})

function handleChange(changes: Partial<QuickNotePayload>): void {
  emit('update:modelValue', { ...props.modelValue, ...changes })
}
function onTextInput(event: Event, key: 'title' | 'body'): void {
  handleChange({ [key]: (event.target as HTMLInputElement).value })
}
function onTaskInput(event: Event, index: number): void {
  handleChange({ tasks: props.modelValue.tasks.map((task, taskIndex) => taskIndex === index ? { ...task, text: (event.target as HTMLInputElement).value } : task) })
}
function onTaskToggle(index: number): void {
  handleChange({ tasks: props.modelValue.tasks.map((task, taskIndex) => taskIndex === index ? { ...task, done: !task.done } : task) })
}
function onFilesChange(event: Event): void {
  const tempInput = event.target as HTMLInputElement
  if (tempInput.files?.length) emit('upload', tempInput.files)
  tempInput.value = ''
}

function handleQuickAction(type: 'image' | 'task' | 'tag'): void {
  if (isLocked.value) return
  if (type === 'image') fileInputRef.value?.click()
  if (type === 'tag') tagsInputRef.value?.focus()
}

defineExpose({ handleQuickAction })
</script>

<template>
  <aside class="note-editor" aria-labelledby="note-editor-heading" @keydown.ctrl.enter.prevent="emit('save')" @keydown.meta.enter.prevent="emit('save')">
    <header class="editor-heading">
      <h2 id="note-editor-heading">{{ isReadOnly ? '查看笔记 · 只读' : isNew ? '新建笔记' : '编辑笔记' }}</h2>
      <div class="editor-heading-actions">
        <button v-if="!isReadOnly" type="button" class="icon-button" :aria-label="modelValue.pinned ? '取消置顶' : '置顶'" :aria-pressed="modelValue.pinned" :disabled="isLocked" @click="handleChange({ pinned: !modelValue.pinned })"><component :is="modelValue.pinned ? StarFilled : Star" /></button>
        <button type="button" class="icon-button" aria-label="清空当前笔记" title="清空当前笔记" :disabled="isSaving || isUploading" @click="emit('cancel')"><close /></button>
      </div>
    </header>
    <div class="editor-scroll">
      <label class="sr-only" for="note-title">笔记标题</label>
      <input id="note-title" class="editor-title" :value="modelValue.title" :readonly="isLocked" maxlength="200" placeholder="无标题笔记" @input="onTextInput($event, 'title')" />
      <div v-if="modelValue.imageIds.length" class="editor-images">
        <div v-for="(fileId, index) in modelValue.imageIds" :key="fileId" class="editor-image-wrap">
          <note-image :file-id="fileId" :alt="`笔记图片 ${index + 1}`" />
          <button v-if="!isReadOnly" type="button" class="remove-image icon-button" :disabled="isLocked" :aria-label="`移除图片 ${index + 1}`" @click="handleChange({ imageIds: modelValue.imageIds.filter((id) => id !== fileId) })"><close /></button>
        </div>
      </div>
      <label class="sr-only" for="note-body">笔记正文</label>
      <textarea id="note-body" class="editor-body" :value="modelValue.body" :readonly="isLocked" maxlength="10000" placeholder="记下想法，也记住生活…" @input="onTextInput($event, 'body')"></textarea>
    </div>
    <div class="editor-details">
      <div class="editor-checklist">
        <div v-for="(task, index) in modelValue.tasks" :key="index" class="editor-task" :class="{ done: task.done }">
          <input type="checkbox" :checked="task.done" :disabled="isLocked" :aria-label="`完成待办 ${index + 1}`" @change="onTaskToggle(index)" />
          <input :value="task.text" :readonly="isLocked" maxlength="300" :aria-label="`待办内容 ${index + 1}`" placeholder="待办事项" @input="onTaskInput($event, index)" />
          <button v-if="!isReadOnly" type="button" class="icon-button" :aria-label="`删除待办 ${index + 1}`" :disabled="isLocked" @click="handleChange({ tasks: modelValue.tasks.filter((_, taskIndex) => taskIndex !== index) })"><delete /></button>
        </div>
        <button v-if="!isReadOnly" type="button" class="add-task" :disabled="isLocked || modelValue.tasks.length >= 100" @click="handleChange({ tasks: [...modelValue.tasks, { text: '', done: false }] })"><plus />添加待办</button>
      </div>
      <div class="editor-tag-row">
        <label class="tag-label" for="note-tags">标签</label>
        <input id="note-tags" ref="tagsInputRef" v-model="tagsText" class="editor-tags" :readonly="isLocked" placeholder="例如：生活，灵感" />
      </div>
      <p v-if="isUploading" class="upload-state" role="status">图片上传中，请稍候…</p>
    </div>
    <footer class="editor-footer">
      <template v-if="!isReadOnly">
        <input ref="fileInputRef" class="sr-only" type="file" accept="image/jpeg,image/png,image/webp,image/gif" multiple :disabled="isLocked" @change="onFilesChange" />
        <button type="button" class="icon-button" aria-label="添加图片" :disabled="isLocked || modelValue.imageIds.length >= 9" @click="fileInputRef?.click()"><picture-icon /></button>
        <button type="button" class="icon-button" aria-label="添加待办" :disabled="isLocked || modelValue.tasks.length >= 100" @click="handleChange({ tasks: [...modelValue.tasks, { text: '', done: false }] })"><checklist-icon /></button>
      </template>
      <div class="footer-spacer"></div>
      <button type="button" class="editor-cancel secondary-button" :disabled="isSaving || isUploading" @click="emit('cancel')">{{ isReadOnly ? '关闭' : '取消' }}</button>
      <button v-if="!isReadOnly" type="button" class="editor-save primary-button" :disabled="isSaving || isUploading" @click="emit('save')">{{ isSaving ? '保存中…' : '保存' }}</button>
    </footer>
  </aside>
</template>

<style scoped>
.note-editor { width:360px; min-width:320px; flex-shrink:0; height:100%; display:flex; flex-direction:column; background:#fff; border-left:1px solid #e8ede9; box-shadow:-4px 0 18px #1d372305; }
.editor-heading { display:flex; flex-shrink:0; align-items:center; justify-content:space-between; height:36px; box-sizing:border-box; padding:0 22px; border-bottom:1px solid #edf0ed; }h2 { margin:0; font-size:15px; font-weight:650; }.editor-heading-actions { display:flex; gap:10px; }
.icon-button { display:grid; place-items:center; width:30px; height:30px; padding:5px; border:0; border-radius:6px; background:none; color:#718078; cursor:pointer; }.icon-button[aria-pressed="true"] { color:#236b4e; }.icon-button:hover { background:#edf5f0; }.icon-button svg { width:18px; height:18px; }
.editor-scroll { display:flex; flex-direction:column; flex:1; overflow:hidden; min-height:0; padding:20px 22px 12px; }.editor-title { flex-shrink:0; width:100%; border:0; background:none; outline:none; color:#202b24; font-size:12.5px; font-weight:650; margin:0 0 16px; padding:0; line-height:1.6; }
.editor-images { display:grid; flex-shrink:0; max-height:35%; overflow-y:auto; gap:12px; margin-bottom:12px; }.editor-image-wrap { position:relative; }.remove-image { position:absolute; top:6px; right:6px; background:#fffffff0; box-shadow:0 1px 6px #0002; }
.editor-body { flex:1; resize:none; min-height:0; width:100%; box-sizing:border-box; overflow-y:auto; padding:0; border:0; outline:none; background:transparent; color:#39443d; font:inherit; line-height:1.9; font-size:14px; }.editor-body::placeholder,.editor-title::placeholder { color:#a0a9a3; }
.editor-details { flex-shrink:0; max-height:40%; overflow-y:auto; padding:0 22px 12px; }
.editor-checklist { margin:0 0 12px; }.editor-task { display:flex; align-items:center; gap:10px; margin:5px 0; }.editor-task input[type="checkbox"] { width:18px; height:18px; flex-shrink:0; accent-color:#236b4e; }.editor-task input[type="text"],.editor-task input:not([type]) { min-width:0; width:100%; border:0; border-bottom:1px solid transparent; padding:8px 0; outline:none; font:inherit; color:#39443d; background:none; }.editor-task input:focus { border-bottom-color:#c6d9cd; }.editor-task.done input:not([type="checkbox"]) { color:#949c96; text-decoration:line-through; }.editor-task .icon-button { flex-shrink:0; width:26px; }
.add-task { display:flex; align-items:center; gap:8px; border:0; background:none; padding:8px 0; color:#547f64; font:inherit; font-size:13px; cursor:pointer; }.add-task svg { width:15px; height:15px; }
.editor-tag-row { display:flex; align-items:center; gap:12px; }.tag-label { flex-shrink:0; color:#5b6c60; font-size:12px; }.editor-tags { flex:1; min-width:0; box-sizing:border-box; padding:10px 12px; border:1px solid #e7ece8; border-radius:8px; font:inherit; font-size:13px; color:#426950; outline:none; }.editor-tags:focus { border-color:#77a18b; }
.upload-state { color:#236b4e; font-size:12px; }
.editor-footer { display:flex; flex-shrink:0; align-items:center; height:46px; box-sizing:border-box; gap:10px; border-top:1px solid #edf0ed; padding:0 20px; }.footer-spacer { flex:1; }.primary-button,.secondary-button { height:30px; border:0; border-radius:4px; padding:0 17px; font:inherit; font-size:13px; line-height:30px; cursor:pointer; }.primary-button { background:#236b4e; color:white; }.secondary-button { background:#f2f4f3; color:#626f67; }.editor-footer .icon-button { width:24px; height:30px; padding:6px 3px; }.editor-footer .icon-button svg { width:16px; height:16px; }button:disabled { opacity:.45; cursor:default; }button:focus-visible { outline:2px solid #236b4e; outline-offset:3px; }
.sr-only { position:absolute; width:1px; height:1px; padding:0; margin:-1px; overflow:hidden; clip:rect(0,0,0,0); white-space:nowrap; border:0; }
@media(max-width:1100px) { .note-editor { width:340px; } }
@media(max-width:850px) { .note-editor { position:fixed; z-index:35; right:0; top:0; bottom:0; width:min(440px,calc(100vw - 72px)); min-width:0; box-shadow:-10px 0 40px #1c3b2430; } }
</style>
