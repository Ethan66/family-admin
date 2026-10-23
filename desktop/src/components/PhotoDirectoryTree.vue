<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from 'vue'
import { ArrowRight, Delete, Document, FolderAdd, FolderOpened, PictureFilled, Plus } from '@element-plus/icons-vue'
import type { PhotoDirectoryNode } from '@/utils/photoDirectory'

const props = defineProps<{ nodes: PhotoDirectoryNode[]; selectedPath: string | null; totalCount: number; addDisabled: boolean; removablePaths: string[]; removeDisabled: boolean; createDisabled?: boolean; createSubfolder?: (parentPath: string, folderName: string) => Promise<boolean>; jsonRootPaths?: string[]; draggingPhotos?: boolean }>()
const emit = defineEmits<{ select: [path: string | null]; addDirectory: []; removeDirectory: [path: string]; deleteSubfolder: [path: string]; dropPhotos: [path: string] }>()
const expandedPaths = ref<string[]>([])
const activeDropPath = ref<string | null>(null)
const creatingParentPathData = ref<string | null>(null)
const folderNameData = ref('')
const isSubmittingCreate = ref(false)
const folderNameRef = ref<HTMLInputElement | null>(null)
const canRemoveSelectedDirectory = computed(() => props.selectedPath !== null && props.removablePaths.includes(props.selectedPath) && !props.removeDisabled)
const canCreateSubfolder = computed(() => !props.createDisabled && !!props.createSubfolder)
const visibleNodes = computed(() => {
  const tempVisibleNodes: { node: PhotoDirectoryNode; level: number; rootPath: string }[] = []
  function appendVisible(nodes: PhotoDirectoryNode[], level: number, rootPath = ''): void {
    for (const tempNode of nodes) {
      const tempRootPath = level === 1 ? tempNode.path : rootPath
      tempVisibleNodes.push({ node: tempNode, level, rootPath: tempRootPath })
      if (expandedPaths.value.includes(tempNode.path)) appendVisible(tempNode.children, level + 1, tempRootPath)
    }
  }
  appendVisible(props.nodes, 1)
  return tempVisibleNodes
})

watch(() => props.draggingPhotos, (dragging) => {
  if (!dragging) activeDropPath.value = null
})
watch(() => props.nodes, () => {
  if (props.selectedPath !== null && !expandedPaths.value.includes(props.selectedPath)) {
    expandedPaths.value = [...expandedPaths.value, props.selectedPath]
  }
})
watch(() => props.selectedPath, (path) => {
  if (creatingParentPathData.value !== path && !isSubmittingCreate.value) {
    creatingParentPathData.value = null
    folderNameData.value = ''
  }
})

function isExpanded(path: string): boolean {
  return expandedPaths.value.includes(path)
}

function onToggleDirectory(path: string): void {
  expandedPaths.value = isExpanded(path)
    ? expandedPaths.value.filter((tempPath) => tempPath !== path)
    : [...expandedPaths.value, path]
}

function onSelectDirectory(path: string): void {
  emit('select', path)
}

function onSelectAll(): void {
  emit('select', null)
}

function onAddDirectory(): void {
  emit('addDirectory')
}

async function onStartCreateSubfolder(path: string): Promise<void> {
  if (!canCreateSubfolder.value || isSubmittingCreate.value) return
  if (props.selectedPath !== path) emit('select', path)
  creatingParentPathData.value = path
  folderNameData.value = ''
  await nextTick()
  folderNameRef.value?.focus()
}

function onCancelCreateSubfolder(): void {
  if (isSubmittingCreate.value) return
  creatingParentPathData.value = null
  folderNameData.value = ''
}

function setFolderNameRef(element: Element | ComponentPublicInstance | null): void {
  folderNameRef.value = element instanceof HTMLInputElement ? element : null
}

/** 成功后展开父目录；失败时保留名称，以便就地修改后重试。 */
async function onSubmitCreateSubfolder(): Promise<void> {
  const tempParentPath = creatingParentPathData.value
  const tempFolderName = folderNameData.value.trim()
  if (!tempParentPath || !tempFolderName || !canCreateSubfolder.value || !props.createSubfolder || isSubmittingCreate.value) return
  isSubmittingCreate.value = true
  try {
    const res = await props.createSubfolder(tempParentPath, tempFolderName)
    if (!res) return
    if (!expandedPaths.value.includes(tempParentPath)) expandedPaths.value = [...expandedPaths.value, tempParentPath]
    creatingParentPathData.value = null
    folderNameData.value = ''
  } finally {
    isSubmittingCreate.value = false
  }
}

/** 仅向上层请求移除索引中显式添加的目录，不对扫描所得子文件夹发出操作。 */
function onRemoveDirectory(): void {
  if (!canRemoveSelectedDirectory.value || props.selectedPath === null) return
  emit('removeDirectory', props.selectedPath)
}

/** 子文件夹删除由上层确认，根目录只能执行索引移除。 */
function onDeleteSubfolder(path: string): void {
  if (props.removeDisabled || props.removablePaths.includes(path)) return
  emit('deleteSubfolder', path)
}

/** 只接收应用内已选照片；目录行可投放，索引文件与“全部照片”不可投放。 */
function onDragOverDirectory(event: DragEvent, path: string): void {
  if (!props.draggingPhotos || props.removeDisabled) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  activeDropPath.value = path
}

function onDragLeaveDirectory(event: DragEvent, path: string): void {
  if (activeDropPath.value !== path) return
  const tempNextTarget = event.relatedTarget
  if (tempNextTarget instanceof Node && (event.currentTarget as HTMLElement).contains(tempNextTarget)) return
  activeDropPath.value = null
}

function onDropPhotos(event: DragEvent, path: string): void {
  if (!props.draggingPhotos || props.removeDisabled) return
  event.preventDefault()
  event.stopPropagation()
  activeDropPath.value = null
  emit('dropPhotos', path)
}
</script>

<template>
  <aside class="photo-directory-tree" aria-label="照片目录">
    <div class="tree-heading">
      <span>目录</span>
      <div class="tree-heading-actions">
        <button class="tree-add" type="button" title="添加照片目录" aria-label="添加照片目录" :disabled="addDisabled" @click="onAddDirectory"><el-icon><folder-add /></el-icon></button>
        <button class="tree-add tree-remove" type="button" title="移除照片目录" aria-label="移除照片目录" :disabled="!canRemoveSelectedDirectory" @click="onRemoveDirectory"><el-icon><delete /></el-icon></button>
      </div>
    </div>
    <button class="tree-all" :class="{ 'is-active': selectedPath === null }" type="button" @click="onSelectAll">
      <el-icon><picture-filled /></el-icon>
      <span>全部照片</span>
      <small>{{ totalCount }}</small>
    </button>
    <ul class="tree-list" role="tree" aria-label="照片文件夹">
      <template v-for="(item, index) in visibleNodes" :key="item.node.path">
      <li
        class="tree-item"
        role="treeitem"
        :aria-level="item.level"
        :aria-selected="selectedPath === item.node.path"
        :aria-expanded="item.node.children.length || item.level === 1 ? isExpanded(item.node.path) : undefined"
      >
        <div class="tree-row" :class="{ 'is-active': selectedPath === item.node.path, 'is-drop-target': activeDropPath === item.node.path && draggingPhotos }" :style="{ paddingLeft: `${(item.level - 1) * 14 + 6}px` }" @dragover="onDragOverDirectory($event, item.node.path)" @dragleave="onDragLeaveDirectory($event, item.node.path)" @drop="onDropPhotos($event, item.node.path)">
          <button
            v-if="item.node.children.length || item.level === 1"
            class="tree-toggle"
            type="button"
            :aria-label="`${isExpanded(item.node.path) ? '折叠' : '展开'} ${item.node.name}`"
            @click="onToggleDirectory(item.node.path)"
          ><el-icon :class="{ 'is-expanded': isExpanded(item.node.path) }"><arrow-right /></el-icon></button>
          <span v-else class="tree-toggle-spacer" />
          <button class="tree-node" type="button" :data-testid="`directory-${item.node.path}`" :title="item.node.path" @click="onSelectDirectory(item.node.path)">
            <el-icon><folder-opened /></el-icon>
            <span class="tree-name">{{ item.node.name }}</span>
            <small>{{ item.node.count }}</small>
          </button>
          <button v-if="createSubfolder" class="tree-row-create" type="button" title="新建子文件夹" aria-label="新建子文件夹" :disabled="!canCreateSubfolder" @click.stop="onStartCreateSubfolder(item.node.path)"><el-icon><plus /></el-icon></button>
          <button v-if="!removablePaths.includes(item.node.path)" class="tree-delete-child" type="button" :aria-label="`删除子文件夹 ${item.node.name}`" :title="`将 ${item.node.name} 移入回收站`" :disabled="removeDisabled" @click.stop="onDeleteSubfolder(item.node.path)"><el-icon><delete /></el-icon></button>
        </div>
        <div v-if="creatingParentPathData === item.node.path" class="tree-create-row" :style="{ paddingLeft: `${item.level * 14 + 6}px` }">
          <input :ref="setFolderNameRef" v-model="folderNameData" type="text" aria-label="子文件夹名称" placeholder="输入子文件夹名称" :disabled="isSubmittingCreate" @keydown.enter.prevent="onSubmitCreateSubfolder" @keydown.esc.stop.prevent="onCancelCreateSubfolder" />
        </div>
      </li>
      <li v-if="isExpanded(item.rootPath) && jsonRootPaths?.includes(item.rootPath) && (!visibleNodes[index + 1] || visibleNodes[index + 1].rootPath !== item.rootPath)" :data-testid="`photo-root-json-${item.rootPath}`" class="tree-item tree-json-item" role="treeitem" aria-level="2" aria-disabled="true" :title="`${item.rootPath}/photos-setting.json`">
        <div class="tree-row tree-json-row" style="padding-left:20px"><span class="tree-toggle-spacer" /><el-icon><document /></el-icon><span class="tree-name">photos-setting.json</span></div>
      </li>
      </template>
    </ul>
    <div v-if="!nodes.length" class="tree-empty">添加照片目录并扫描后显示文件夹</div>
  </aside>
</template>

<style scoped>
.photo-directory-tree { min-width:0; max-height:calc(100vh - 142px); overflow:auto; padding:16px 10px; border:1px solid var(--border); border-radius:12px; background:var(--surface); }
.tree-heading { display:flex; align-items:center; justify-content:space-between; min-height:32px; padding:0 4px 8px 10px; color:var(--text-3); font-size:12px; font-weight:600; }
.tree-heading-actions { display:flex; align-items:center; gap:2px; }
.tree-add { display:grid; width:26px; height:26px; flex:none; place-items:center; padding:0; border:0; border-radius:6px; background:transparent; color:var(--text-2); cursor:pointer; font-size:16px; }
.tree-remove:hover:not(:disabled) { color:#b64646; background:#fceeee; }
.tree-add:hover:not(:disabled) { background:var(--primary-weak); color:var(--primary); }.tree-add:disabled { opacity:.55; cursor:default; }.tree-add:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }
.tree-row-create { display:grid; width:24px; height:24px; flex:none; place-items:center; padding:0; border:0; border-radius:4px; background:transparent; color:var(--text-2); cursor:pointer; opacity:0; pointer-events:none; }.tree-row:hover .tree-row-create,.tree-row-create:focus-visible { opacity:1; pointer-events:auto; }.tree-row-create:hover:not(:disabled) { background:var(--primary-weak); color:var(--primary); }.tree-row-create:disabled { cursor:default; }
.tree-create-row { display:flex; align-items:center; min-width:0; padding-right:6px; }.tree-create-row input { width:100%; min-width:0; height:28px; padding:0 7px; border:1px solid var(--primary); border-radius:4px; background:var(--surface); color:var(--text-1); font:inherit; font-size:12px; outline:none; }
.tree-all,.tree-row { display:flex; align-items:center; box-sizing:border-box; width:100%; height:34px; border:0; border-radius:7px; background:transparent; color:var(--text-2); font:inherit; font-size:12px; text-align:left; }
.tree-row { position:relative; padding-right:34px; }
.tree-all { gap:8px; padding:0 9px; cursor:pointer; }.tree-all span { flex:1; }.tree-all small,.tree-node small { color:var(--text-3); font-size:11px; }
.tree-all:hover,.tree-row:hover { background:var(--surface-sunken); }.tree-all.is-active,.tree-row.is-active { background:var(--primary-weak); color:var(--primary); }
.tree-row.is-drop-target { background:var(--primary-weak); color:var(--primary); outline:2px solid var(--primary); outline-offset:-2px; }
.tree-list { margin:8px 0 0; padding:0; list-style:none; }.tree-item { margin:2px 0; }.tree-toggle,.tree-toggle-spacer { flex:none; width:19px; height:25px; }.tree-toggle { display:grid; place-items:center; padding:0; border:0; background:transparent; color:var(--text-3); cursor:pointer; }.tree-toggle .el-icon { transition:transform .15s; }.tree-toggle .el-icon.is-expanded { transform:rotate(90deg); }
.tree-node { display:flex; align-items:center; gap:7px; min-width:0; height:100%; flex:1; padding:0 7px 0 2px; border:0; background:transparent; color:inherit; font:inherit; text-align:left; cursor:pointer; }.tree-name { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.tree-empty { padding:14px 10px; color:var(--text-3); font-size:12px; line-height:1.5; }
.tree-node small { position:absolute; top:50%; right:8px; width:26px; transform:translateY(-50%); text-align:right; }
.tree-delete-child { display:grid; width:24px; height:24px; flex:none; place-items:center; padding:0; border:0; border-radius:4px; background:transparent; color:var(--text-3); cursor:pointer; opacity:0; }.tree-row:hover .tree-delete-child,.tree-delete-child:focus-visible { opacity:1; }.tree-delete-child:hover:not(:disabled) { color:#b64646; background:#fceeee; }.tree-delete-child:disabled { cursor:default; }.tree-json-row { color:var(--text-3); cursor:default; }.tree-json-row:hover { background:transparent; }.tree-json-row .el-icon { font-size:14px; }
</style>
