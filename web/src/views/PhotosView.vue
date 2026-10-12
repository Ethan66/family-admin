<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile } from 'element-plus'
import { filesApi, photosApi } from '@/api/modules'
import type { Photo } from '@/types'
const rows = ref<Photo[]>([]),
  loading = ref(false),
  dialog = ref(false),
  uploading = ref(false),
  imageUrls = ref<Record<number, string>>({})
const query = reactive({ album: '', keyword: '', page: 1, pageSize: 40 })
const form = reactive({
  title: '',
  album: '家庭',
  photoDate: new Date().toISOString().slice(0, 10),
  description: '',
  fileId: 0,
})
function clearUrls() {
  Object.values(imageUrls.value).forEach(URL.revokeObjectURL)
  imageUrls.value = {}
}
async function load() {
  loading.value = true
  try {
    rows.value = (await photosApi.list(query)).records
    clearUrls()
    const entries = await Promise.all(
      rows.value.map(
        async (row) => [row.id, URL.createObjectURL(await filesApi.content(row.fileId))] as const,
      ),
    )
    imageUrls.value = Object.fromEntries(entries)
  } finally {
    loading.value = false
  }
}
async function select(file: UploadFile) {
  if (!file.raw) return
  uploading.value = true
  try {
    const result = await photosApi.upload(file.raw)
    form.fileId = result.id
    ElMessage.success('图片上传完成')
  } finally {
    uploading.value = false
  }
}
async function save() {
  if (!form.fileId) return ElMessage.warning('请先选择图片')
  await photosApi.create(form)
  dialog.value = false
  Object.assign(form, {
    title: '',
    album: '家庭',
    photoDate: new Date().toISOString().slice(0, 10),
    description: '',
    fileId: 0,
  })
  load()
}
async function del(id: number) {
  await ElMessageBox.confirm('确认删除照片资料？', '删除照片', {
    type: 'warning',
  })
  await photosApi.remove(id)
  load()
}
onMounted(load)
onBeforeUnmount(clearUrls)
</script>
<template>
  <section class="workspace">
    <header class="page-head">
      <div>
        <h1>照片管理</h1>
        <p>把散落的家庭影像按相册收好</p>
      </div>
      <el-button type="primary" @click="dialog = true">上传照片</el-button>
    </header>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜索照片标题"
        class="max-w-56"
      /><el-input v-model="query.album" clearable placeholder="相册" class="max-w-40" /><el-button
        @click="load"
        >筛选</el-button
      >
    </div>
    <div v-loading="loading" class="photo-grid">
      <article v-for="photo in rows" :key="photo.id" class="photo-item">
        <el-image
          :src="imageUrls[photo.id]"
          :preview-src-list="Object.values(imageUrls)"
          fit="cover"
          lazy
        />
        <div>
          <strong>{{ photo.title }}</strong
          ><span>{{ photo.album }} · {{ photo.photoDate }}</span>
        </div>
        <el-button class="delete" circle text type="danger" @click="del(photo.id)">×</el-button>
      </article>
    </div>
    <el-empty
      v-if="!loading && !rows.length"
      description="还没有照片，上传第一张家庭影像吧"
    /><el-dialog v-model="dialog" title="上传照片" width="min(520px,92vw)"
      ><el-form label-position="top"
        ><el-upload
          :auto-upload="false"
          :show-file-list="true"
          accept="image/*"
          :limit="1"
          :on-change="select"
          ><el-button :loading="uploading">选择图片</el-button></el-upload
        ><el-form-item label="标题" class="mt-4"><el-input v-model="form.title" /></el-form-item>
        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="相册"><el-input v-model="form.album" /></el-form-item
          ><el-form-item label="拍摄日期"
            ><el-date-picker v-model="form.photoDate" value-format="YYYY-MM-DD" class="w-full"
          /></el-form-item>
        </div>
        <el-form-item label="描述"
          ><el-input v-model="form.description" type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :disabled="!form.fileId" @click="save"
          >保存照片</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 18px;
}
.photo-item {
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  background: var(--paper);
  box-shadow: 0 8px 25px rgba(32, 51, 44, 0.08);
}
.photo-item :deep(.el-image) {
  width: 100%;
  aspect-ratio: 4/3;
  display: block;
  transition: transform 0.25s;
}
.photo-item:hover :deep(.el-image) {
  transform: scale(1.025);
}
.photo-item > div {
  display: grid;
  padding: 14px 16px;
}
.photo-item span {
  margin-top: 5px;
  color: var(--muted);
  font-size: 12px;
}
.delete {
  position: absolute;
  right: 8px;
  top: 8px;
  background: rgba(255, 255, 255, 0.9);
}
</style>
