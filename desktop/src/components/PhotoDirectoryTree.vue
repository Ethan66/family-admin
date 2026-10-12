<script setup lang="ts">
import { computed, ref } from 'vue'
import { ArrowRight, FolderAdd, FolderOpened, PictureFilled } from '@element-plus/icons-vue'
import type { PhotoDirectoryNode } from '@/utils/photoDirectory'

const props = defineProps<{ nodes: PhotoDirectoryNode[]; selectedPath: string | null; totalCount: number; addDisabled: boolean }>()
const emit = defineEmits<{ select: [path: string | null]; addDirectory: [] }>()
const expandedPaths = ref<string[]>([])
const visibleNodes = computed(() => {
  const tempVisibleNodes: { node: PhotoDirectoryNode; level: number }[] = []
  function appendVisible(nodes: PhotoDirectoryNode[], level: number): void {
    for (const tempNode of nodes) {
      tempVisibleNodes.push({ node: tempNode, level })
      if (expandedPaths.value.includes(tempNode.path)) appendVisible(tempNode.children, level + 1)
    }
  }
  appendVisible(props.nodes, 1)
  return tempVisibleNodes
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
</script>

<template>
  <aside class="photo-directory-tree" aria-label="照片目录">
    <div class="tree-heading">
      <span>目录</span>
      <button class="tree-add" type="button" title="添加照片目录" aria-label="添加照片目录" :disabled="addDisabled" @click="onAddDirectory"><el-icon><folder-add /></el-icon></button>
    </div>
    <button class="tree-all" :class="{ 'is-active': selectedPath === null }" type="button" @click="onSelectAll">
      <el-icon><picture-filled /></el-icon>
      <span>全部照片</span>
      <small>{{ totalCount }}</small>
    </button>
    <ul class="tree-list" role="tree" aria-label="照片文件夹">
      <li
        v-for="item in visibleNodes"
        :key="item.node.path"
        class="tree-item"
        role="treeitem"
        :aria-level="item.level"
        :aria-selected="selectedPath === item.node.path"
        :aria-expanded="item.node.children.length ? isExpanded(item.node.path) : undefined"
      >
        <div class="tree-row" :class="{ 'is-active': selectedPath === item.node.path }" :style="{ paddingLeft: `${(item.level - 1) * 14 + 6}px` }">
          <button
            v-if="item.node.children.length"
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
        </div>
      </li>
    </ul>
    <div v-if="!nodes.length" class="tree-empty">添加照片目录并扫描后显示文件夹</div>
  </aside>
</template>

<style scoped>
.photo-directory-tree { min-width:0; max-height:calc(100vh - 142px); overflow:auto; padding:16px 10px; border:1px solid var(--border); border-radius:12px; background:var(--surface); }
.tree-heading { display:flex; align-items:center; justify-content:space-between; min-height:32px; padding:0 4px 8px 10px; color:var(--text-3); font-size:12px; font-weight:600; }
.tree-add { display:grid; width:26px; height:26px; flex:none; place-items:center; padding:0; border:0; border-radius:6px; background:transparent; color:var(--text-2); cursor:pointer; font-size:16px; }
.tree-add:hover:not(:disabled) { background:var(--primary-weak); color:var(--primary); }.tree-add:disabled { opacity:.55; cursor:default; }.tree-add:focus-visible { outline:2px solid var(--primary); outline-offset:1px; }
.tree-all,.tree-row { display:flex; align-items:center; width:100%; height:34px; border:0; border-radius:7px; background:transparent; color:var(--text-2); font:inherit; font-size:12px; text-align:left; }
.tree-all { gap:8px; padding:0 9px; cursor:pointer; }.tree-all span { flex:1; }.tree-all small,.tree-node small { color:var(--text-3); font-size:11px; }
.tree-all:hover,.tree-row:hover { background:var(--surface-sunken); }.tree-all.is-active,.tree-row.is-active { background:var(--primary-weak); color:var(--primary); }
.tree-list { margin:8px 0 0; padding:0; list-style:none; }.tree-item { margin:2px 0; }.tree-toggle,.tree-toggle-spacer { flex:none; width:19px; height:25px; }.tree-toggle { display:grid; place-items:center; padding:0; border:0; background:transparent; color:var(--text-3); cursor:pointer; }.tree-toggle .el-icon { transition:transform .15s; }.tree-toggle .el-icon.is-expanded { transform:rotate(90deg); }
.tree-node { display:flex; align-items:center; gap:7px; min-width:0; height:100%; flex:1; padding:0 7px 0 2px; border:0; background:transparent; color:inherit; font:inherit; text-align:left; cursor:pointer; }.tree-name { flex:1; min-width:0; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }.tree-empty { padding:14px 10px; color:var(--text-3); font-size:12px; line-height:1.5; }
</style>
