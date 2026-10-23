<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { PictureFilled } from '@element-plus/icons-vue'
import { readBinaryPreview } from '@/api/resources'
import type { ResourceEntry } from '@/types/resource'

interface Props {
  familyId: number
  resource: ResourceEntry
}

const props = defineProps<Props>()
const rootRef = ref<HTMLElement>()
const thumbnailData = ref('')
let observer: IntersectionObserver | null = null
let hasBeenVisible = false
let loadVersion = 0

watch([() => props.familyId, () => props.resource.path, () => props.resource.size, () => props.resource.modifiedAt], () => {
  loadVersion += 1
  thumbnailData.value = ''
  if (hasBeenVisible) void handleVisible()
})

/** 只读取可视照片；切换资源时忽略旧请求结果，避免旧图覆盖新图。 */
async function handleVisible(): Promise<void> {
  observer?.disconnect()
  hasBeenVisible = true
  const tempLoadVersion = ++loadVersion
  const tempFamilyId = props.familyId
  const tempResource = props.resource
  if (tempResource.size > 10 * 1024 * 1024) return
  try {
    const res = await readBinaryPreview(tempFamilyId, tempResource.path)
    if (tempLoadVersion !== loadVersion) return
    const tempImage = new Image()
    const tempSource = `data:${res.mimeType};base64,${res.base64}`
    tempImage.src = tempSource
    await tempImage.decode()
    if (tempLoadVersion !== loadVersion) return
    const tempScale = Math.min(1, 480 / Math.max(tempImage.width, tempImage.height))
    const tempCanvas = document.createElement('canvas')
    tempCanvas.width = Math.max(1, Math.round(tempImage.width * tempScale))
    tempCanvas.height = Math.max(1, Math.round(tempImage.height * tempScale))
    tempCanvas.getContext('2d')?.drawImage(tempImage, 0, 0, tempCanvas.width, tempCanvas.height)
    thumbnailData.value = tempCanvas.toDataURL('image/jpeg', 0.75)
  } catch {
    // 文件无法生成缩略图时仍可通过资源卡片打开原文件预览。
  }
}

onMounted(() => {
  if (!rootRef.value) return
  observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) void handleVisible()
  }, { rootMargin: '160px' })
  observer.observe(rootRef.value)
})
onBeforeUnmount(() => {
  loadVersion += 1
  observer?.disconnect()
})
</script>

<template>
  <div ref="rootRef" class="photo-thumb">
    <img v-if="thumbnailData" :src="thumbnailData" :alt="resource.name" />
    <el-icon v-else><picture-filled /></el-icon>
  </div>
</template>

<style scoped>
.photo-thumb { display:grid; width:100%; aspect-ratio:4 / 3; overflow:hidden; place-items:center; background:#edf3ee; color:#7f9f89; font-size:30px; }
.photo-thumb img { width:100%; height:100%; object-fit:contain; }
</style>
