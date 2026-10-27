<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { quickNotesApi } from '@/api/quickNotes'

const props = defineProps<{ fileId: number; alt: string }>()
const imageUrl = ref('')
const isFailed = ref(false)
let controller: AbortController | null = null

watch(() => props.fileId, async (fileId, _, onCleanup) => {
  const tempController = new AbortController()
  controller = tempController
  isFailed.value = false
  imageUrl.value = ''
  let tempUrl = ''
  onCleanup(() => {
    tempController.abort()
    if (tempUrl) URL.revokeObjectURL(tempUrl)
  })
  try {
    const res = await quickNotesApi.apiImage(fileId, tempController.signal)
    if (tempController.signal.aborted) return
    tempUrl = URL.createObjectURL(res)
    imageUrl.value = tempUrl
  } catch {
    // 图片加载失败只影响预览，不阻断笔记正文；取消的请求不显示失败占位。
    if (!tempController.signal.aborted) isFailed.value = true
  }
}, { immediate: true })

onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <div class="note-image">
    <img v-if="imageUrl" :src="imageUrl" :alt="alt" loading="lazy" />
    <span v-else role="status">{{ isFailed ? '图片暂时无法加载' : '正在加载图片…' }}</span>
  </div>
</template>

<style scoped>
.note-image { display:grid; place-items:center; min-height:100px; overflow:hidden; border-radius:9px; background:#edf1ee; color:#78847d; font-size:12px; }
img { display:block; width:100%; max-height:270px; object-fit:cover; }
</style>
