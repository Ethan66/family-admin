<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FolderOpened } from '@element-plus/icons-vue'

interface PhotoDirectoryOption {
  path: string
  label: string
  isRoot: boolean
}

interface Props {
  variant: 'batch' | 'single'
  resourceIds: string[]
  directories: PhotoDirectoryOption[]
  currentDirectory: string | null
  disabled: boolean
  moving: boolean
  moveAction: (resourceIds: string[], destinationParent: string, newFolderName: string | null) => Promise<boolean>
}

const props = defineProps<Props>()
const dropdownRef = ref<HTMLElement | null>(null)
const searchInputRef = ref<HTMLInputElement | null>(null)
const isVisible = ref(false)
const isSubmitting = ref(false)
const query = ref('')
const createParentPath = ref('')

const childDirectories = computed(() => props.directories.filter((option) => !option.isRoot))
const filteredDirectories = computed(() => {
  const tempQuery = query.value.trim().toLocaleLowerCase()
  if (!tempQuery) return childDirectories.value
  return childDirectories.value.filter((option) => option.label.trim().toLocaleLowerCase().includes(tempQuery))
})
const canCreate = computed(() => query.value.trim().length > 0 && filteredDirectories.value.length === 0 && !!createParentPath.value)

watch(() => props.resourceIds.join('\u0000'), () => {
  isVisible.value = false
})

/** 每次展开根据当前目录确定新建位置，避免沿用上一次的搜索和目录。 */
async function onToggle(): Promise<void> {
  if (props.disabled || props.moving || isSubmitting.value || !props.resourceIds.length) return
  isVisible.value = !isVisible.value
  if (!isVisible.value) return
  query.value = ''
  const tempCurrentDirectory = props.directories.find((option) => option.path === props.currentDirectory)
  createParentPath.value = tempCurrentDirectory?.path ?? props.directories.find((option) => option.isRoot)?.path ?? ''
  await nextTick()
  searchInputRef.value?.focus()
}

/** 移动失败时保留面板和搜索内容，便于用户换一个目标重试。 */
async function handleMoveToExisting(destinationPath: string): Promise<void> {
  if (isSubmitting.value || props.moving) return
  isSubmitting.value = true
  try {
    const tempMoved = await props.moveAction([...props.resourceIds], destinationPath, null)
    if (tempMoved) isVisible.value = false
  } finally {
    isSubmitting.value = false
  }
}

/** 无匹配时把输入当作新文件夹名称，仍由现有桌面命令负责校验和实际创建。 */
async function handleCreateAndMove(): Promise<void> {
  if (!canCreate.value || isSubmitting.value || props.moving) return
  isSubmitting.value = true
  try {
    const tempMoved = await props.moveAction([...props.resourceIds], createParentPath.value, query.value.trim())
    if (tempMoved) isVisible.value = false
  } finally {
    isSubmitting.value = false
  }
}

function onSearchKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' || event.isComposing) return
  event.preventDefault()
  event.stopPropagation()
  const tempFirstDirectory = filteredDirectories.value[0]
  if (tempFirstDirectory) {
    void handleMoveToExisting(tempFirstDirectory.path)
    return
  }
  if (canCreate.value) void handleCreateAndMove()
}

function onDocumentPointerDown(event: PointerEvent): void {
  if (!isVisible.value || isSubmitting.value || props.moving) return
  if (event.target instanceof Node && !dropdownRef.value?.contains(event.target)) isVisible.value = false
}

function onEscape(): void {
  if (!isSubmitting.value && !props.moving) isVisible.value = false
}

onMounted(() => document.addEventListener('pointerdown', onDocumentPointerDown))
onBeforeUnmount(() => document.removeEventListener('pointerdown', onDocumentPointerDown))
</script>

<template>
  <div ref="dropdownRef" class="photo-move-dropdown" :class="`photo-move-dropdown--${variant}`">
    <button
      v-if="variant === 'batch'"
      data-testid="batch-move-photos"
      class="photo-move-trigger photo-move-trigger--batch"
      type="button"
      title="移动所选照片"
      aria-label="移动所选照片"
      aria-haspopup="menu"
      :aria-expanded="isVisible"
      :disabled="disabled || moving || isSubmitting || !resourceIds.length"
      @click="onToggle"
    ><el-icon><folder-opened /></el-icon></button>
    <button
      v-else
      data-testid="move-single-photo"
      class="photo-move-trigger photo-move-trigger--single"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="isVisible"
      :disabled="disabled || moving || isSubmitting || !resourceIds.length"
      @click="onToggle"
    ><el-icon><folder-opened /></el-icon><span>移动到子文件夹</span></button>

    <div v-if="isVisible" class="photo-move-menu" role="menu" aria-label="移动照片到子文件夹" @keydown.esc.prevent.stop="onEscape">
      <input
        ref="searchInputRef"
        v-model="query"
        data-testid="photo-move-search"
        class="photo-move-search"
        type="text"
        maxlength="255"
        placeholder="搜索或输入子文件夹名称"
        aria-label="搜索或输入子文件夹名称"
        :disabled="moving || isSubmitting"
        @keydown="onSearchKeydown"
      />
      <div v-if="filteredDirectories.length" class="photo-move-options">
        <button
          v-for="option in filteredDirectories"
          :key="option.path"
          data-testid="photo-move-option"
          class="photo-move-option"
          type="button"
          role="menuitem"
          :title="option.path"
          :disabled="moving || isSubmitting"
          @click="handleMoveToExisting(option.path)"
        ><el-icon><folder-opened /></el-icon><span>{{ option.label.trim() }}</span></button>
      </div>
      <div v-else-if="canCreate" class="photo-move-create-panel">
        <label class="photo-move-parent-label" :for="`photo-move-parent-${variant}`">新建于</label>
        <select :id="`photo-move-parent-${variant}`" v-model="createParentPath" data-testid="photo-move-parent" class="photo-move-parent" aria-label="新文件夹的父目录" :disabled="moving || isSubmitting" @keydown.enter.prevent.stop>
          <option v-for="option in directories" :key="option.path" :value="option.path">{{ option.label }}</option>
        </select>
        <button data-testid="photo-move-create" class="photo-move-create" type="button" :disabled="moving || isSubmitting" @click="handleCreateAndMove">创建“{{ query.trim() }}”并移动</button>
      </div>
      <div v-else class="photo-move-empty">{{ query.trim() ? '没有匹配的子文件夹' : '暂无子文件夹，输入名称后按回车创建' }}</div>
    </div>
  </div>
</template>

<style scoped>
.photo-move-dropdown { position:relative; flex:none; }
.photo-move-dropdown--single { width:100%; margin-bottom:14px; }
.photo-move-trigger { display:flex; align-items:center; justify-content:center; gap:6px; cursor:pointer; }
.photo-move-trigger--batch { width:32px; height:32px; padding:0; border:0; border-radius:7px; background:transparent; color:var(--text-2); font-size:16px; }
.photo-move-trigger--single { width:100%; height:28px; border:1px solid var(--border); border-radius:4px; background:var(--surface); color:var(--text-2); font-size:12px; }
.photo-move-trigger:hover:not(:disabled) { background:var(--primary-weak); color:var(--primary); }
.photo-move-trigger:disabled { color:var(--text-3); opacity:.58; cursor:default; }
.photo-move-trigger:focus-visible,.photo-move-search:focus-visible,.photo-move-parent:focus-visible,.photo-move-option:focus-visible,.photo-move-create:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }
.photo-move-menu { position:absolute; z-index:12; top:calc(100% + 6px); right:0; width:min(280px,calc(100vw - 28px)); padding:8px; border:1px solid var(--border); border-radius:10px; background:var(--surface); box-shadow:0 10px 28px #102a1930; }
.photo-move-dropdown--single .photo-move-menu { width:100%; }
.photo-move-search,.photo-move-parent { width:100%; height:34px; padding:0 9px; border:1px solid var(--border); border-radius:6px; background:var(--surface); color:var(--text); font:inherit; font-size:12px; }
.photo-move-options { max-height:196px; overflow:auto; margin-top:6px; }
.photo-move-option { display:flex; width:100%; align-items:center; gap:7px; padding:8px; border:0; border-radius:6px; background:transparent; color:var(--text-2); text-align:left; cursor:pointer; font-size:12px; }
.photo-move-option span { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.photo-move-option:hover:not(:disabled) { background:var(--primary-weak); color:var(--primary); }
.photo-move-option:disabled,.photo-move-create:disabled { opacity:.6; cursor:default; }
.photo-move-create-panel { display:grid; gap:6px; padding-top:9px; }
.photo-move-parent-label { color:var(--text-3); font-size:11px; }
.photo-move-create { min-height:32px; padding:5px 8px; overflow:hidden; border:0; border-radius:6px; background:var(--primary-weak); color:var(--primary); text-align:left; text-overflow:ellipsis; white-space:nowrap; cursor:pointer; font-size:12px; }
.photo-move-empty { padding:11px 5px 5px; color:var(--text-3); font-size:11px; }
</style>
