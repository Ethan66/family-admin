<script setup lang="ts">
import { computed } from 'vue'
import { MoreFilled, StarFilled } from '@element-plus/icons-vue'
import NoteImage from '@/components/NoteImage.vue'
import type { QuickNote, QuickNotePayload } from '@/types/quickNote'
import { formatNoteTime } from './model'

const props = defineProps<{ note: QuickNote; canWrite: boolean; isBusy: boolean; isSelected: boolean }>()
const emit = defineEmits<{
  open: []
  update: [changes: Partial<QuickNotePayload>]
  remove: []
}>()

// 混合内容优先展示图片类型，其次待办和标签；配色只影响展示，不写入笔记数据。
const cardVariant = computed(() => {
  if (props.note.imageIds.length) return 'image-note'
  if (props.note.tasks.length) return 'task-note'
  if (props.note.tags.length) return 'tag-note'
  return 'text-note'
})

function onTaskChange(index: number): void {
  const tempTasks = props.note.tasks.map((task, taskIndex) => ({ ...task, done: taskIndex === index ? !task.done : task.done }))
  emit('update', { tasks: tempTasks })
}
</script>

<template>
  <article class="note-card" :class="[cardVariant, { selected: isSelected, 'has-image': note.imageIds.length > 0 }]" :aria-busy="isBusy">
    <div class="card-actions">
      <span v-if="note.pinned" class="pinned-label" title="已置顶"><star-filled /></span>
      <details v-if="canWrite" class="note-menu">
        <summary :aria-label="`${note.title || '无标题笔记'}的操作`"><more-filled /></summary>
        <div class="menu-content">
          <button type="button" :disabled="isBusy" @click="emit('update', { pinned: !note.pinned })">{{ note.pinned ? '取消置顶' : '置顶' }}</button>
          <button type="button" :disabled="isBusy" @click="emit('update', { status: note.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED' })">{{ note.status === 'ARCHIVED' ? '取消归档' : '归档' }}</button>
          <button type="button" class="danger" :disabled="isBusy" @click="emit('remove')">删除</button>
        </div>
      </details>
    </div>
    <div class="card-preview">
      <button class="note-open" type="button" :disabled="isBusy" :aria-label="`打开笔记：${note.title || note.body.slice(0, 30) || '未命名笔记'}`" @click="emit('open')">
        <note-image v-if="note.imageIds.length" :file-id="note.imageIds[0]!" :alt="note.title || '笔记图片'" />
        <h3 v-if="note.title">{{ note.title }}</h3>
        <p v-if="note.body" class="card-body">{{ note.body }}</p>
        <p v-if="!note.title && !note.body && !note.tasks.length && !note.imageIds.length" class="card-body">未命名笔记</p>
      </button>
      <div v-if="note.tasks.length" class="card-checklist">
        <label v-for="(task, index) in note.tasks.slice(0, 3)" :key="index" class="card-task" :class="{ done: task.done }">
          <input type="checkbox" :checked="task.done" :disabled="!canWrite || isBusy" @change="onTaskChange(index)" />
          <span>{{ task.text }}</span>
        </label>
        <button v-if="note.tasks.length > 3" type="button" class="more-tasks" @click="emit('open')">另有 {{ note.tasks.length - 3 }} 项</button>
      </div>
    </div>
    <footer class="card-footer">
      <div class="card-tags"><span v-for="tag in note.tags.slice(0, 2)" :key="tag"># {{ tag }}</span><span v-if="note.imageIds.length > 1">{{ note.imageIds.length }} 张图片</span></div>
      <time :datetime="note.updatedAt">{{ formatNoteTime(note.updatedAt) }}</time>
    </footer>
  </article>
</template>

<style scoped>
.note-card { position:relative; display:flex; flex-direction:column; height:180px; min-width:0; box-sizing:border-box; border:1px solid #e7ebe8; border-radius:13px; background:#fff; padding:18px; box-shadow:0 2px 8px #21352b03; transition:border-color .16s,box-shadow .16s; }
.card-preview { display:flex; flex-direction:column; flex:1; min-height:0; overflow:hidden; }
.note-card.task-note { background:#fff8e5; }
.note-card.tag-note { background:#f0f5ef; }
.note-card.image-note { background:#eef5fc; }
.note-card:hover { border-color:#b9cfc1; box-shadow:0 5px 18px #21352b09; }.note-card.selected { border-color:#236b4e; box-shadow:0 0 0 1px #236b4e; }
.note-open { display:block; flex-shrink:1; min-height:0; width:100%; padding:0; overflow:hidden; border:0; background:transparent; text-align:left; color:inherit; cursor:pointer; }.note-open:focus-visible { outline:2px solid #236b4e; outline-offset:-2px; border-radius:4px; }
.note-open:empty { min-height:22px; }.note-open :deep(.note-image) { height:80px; min-height:80px; margin-bottom:12px; }.note-open :deep(img) { height:80px; object-fit:cover; }
h3 { margin:0 44px 10px 0; font-size:17px; font-weight:650; line-height:1.6; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }.card-body { display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; overflow:hidden; margin:0; white-space:pre-wrap; overflow-wrap:anywhere; color:#414b45; line-height:1.85; font-size:14px; }
.has-image .card-body { -webkit-line-clamp:1; }
.card-actions { position:absolute; top:15px; right:12px; display:flex; align-items:center; gap:4px; z-index:2; }.pinned-label { color:#236b4e; display:flex; }.pinned-label svg { width:15px; height:15px; }
summary { display:grid; place-items:center; cursor:pointer; list-style:none; width:26px; height:26px; border-radius:6px; color:#7b847f; }summary::-webkit-details-marker { display:none; }summary:hover { background:#236b4e0d; }summary svg { width:16px; height:16px; }
.menu-content { position:absolute; right:0; top:30px; width:132px; padding:5px; border:1px solid #e7ebe8; border-radius:9px; background:white; box-shadow:0 8px 26px #17291d1c; }.menu-content button { display:block; width:100%; background:none; border:0; padding:8px 10px; text-align:left; border-radius:5px; color:#39473f; cursor:pointer; font:inherit; font-size:13px; }.menu-content button:hover { background:#edf5f0; }.menu-content .danger { color:#b44a41; }
.card-checklist { flex-shrink:0; margin:10px 0 0; display:grid; gap:8px; }.card-task { display:flex; align-items:flex-start; min-width:0; gap:9px; font-size:14px; color:#414b45; line-height:1.6; }.card-task span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.card-task input { width:17px; height:17px; margin:2px 0 0; flex-shrink:0; accent-color:#236b4e; }.card-task.done span { text-decoration:line-through; color:#8c958f; }.more-tasks { border:0; background:none; text-align:left; color:#78847d; cursor:pointer; font:inherit; font-size:12px; }
.card-footer { display:flex; flex-shrink:0; align-items:center; min-height:24px; gap:8px; margin-top:12px; justify-content:space-between; }.card-tags { display:flex; min-width:0; overflow:hidden; gap:5px; }.card-tags span { flex-shrink:0; max-width:100px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:3px 8px; border-radius:20px; background:#eaf3ed; color:#39735a; font-size:11px; }time { color:#8a938d; font-size:11px; white-space:nowrap; margin-left:auto; }
</style>
