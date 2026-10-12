<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { filesApi, moviesApi, photosApi } from '@/api/modules'
import type { Movie } from '@/types'
const rows = ref<Movie[]>([]),
  loading = ref(false),
  dialog = ref(false),
  editing = ref<number | null>(null),
  coverUrls = ref<Record<number, string>>({})
const query = reactive({
  type: '',
  status: '',
  keyword: '',
  page: 1,
  pageSize: 20,
})
const form = reactive({
  title: '',
  type: '电影',
  releaseYear: new Date().getFullYear(),
  director: '',
  minutes: 90,
  rating: 8,
  watchStatus: 'PLANNED',
  progress: 0,
  description: '',
  coverFileId: undefined as number | undefined,
})
function clearUrls() {
  Object.values(coverUrls.value).forEach(URL.revokeObjectURL)
  coverUrls.value = {}
}
async function load() {
  loading.value = true
  try {
    rows.value = (await moviesApi.list(query)).records
    clearUrls()
    const covers = rows.value.filter((row) => row.coverFileId)
    const entries = await Promise.all(
      covers.map(
        async (row) =>
          [row.id, URL.createObjectURL(await filesApi.content(row.coverFileId!))] as const,
      ),
    )
    coverUrls.value = Object.fromEntries(entries)
  } finally {
    loading.value = false
  }
}
function open(row?: Movie) {
  editing.value = row?.id ?? null
  Object.assign(
    form,
    row || {
      title: '',
      type: '电影',
      releaseYear: new Date().getFullYear(),
      director: '',
      minutes: 90,
      rating: 8,
      watchStatus: 'PLANNED',
      progress: 0,
      description: '',
      coverFileId: undefined,
    },
  )
  dialog.value = true
}
async function upload(option: { file: File }) {
  const result = await photosApi.upload(option.file)
  form.coverFileId = result.id
  ElMessage.success('封面上传完成')
}
async function save() {
  editing.value ? await moviesApi.update(editing.value, form) : await moviesApi.create(form)
  dialog.value = false
  ElMessage.success('影片已保存')
  load()
}
async function del(id: number) {
  await ElMessageBox.confirm('确认删除这条影片资料？', '删除影片', {
    type: 'warning',
  })
  await moviesApi.remove(id)
  load()
}
const statusName = (v: string) => ({ PLANNED: '想看', WATCHING: '在看', FINISHED: '看过' })[v] || v
onMounted(load)
onBeforeUnmount(clearUrls)
</script>
<template>
  <section class="workspace">
    <header class="page-head">
      <div>
        <h1>影片管理</h1>
        <p>整理一家人的片单与观看进度，不保存视频文件</p>
      </div>
      <el-button type="primary" @click="open()">添加影片</el-button>
    </header>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="搜索片名"
        class="max-w-52"
      /><el-input v-model="query.type" clearable placeholder="类型" class="max-w-36" /><el-select
        v-model="query.status"
        clearable
        placeholder="观看状态"
        class="w-36"
        ><el-option label="想看" value="PLANNED" /><el-option
          label="在看"
          value="WATCHING" /><el-option label="看过" value="FINISHED" /></el-select
      ><el-button @click="load">筛选</el-button>
    </div>
    <div v-loading="loading" class="movie-list">
      <article v-for="movie in rows" :key="movie.id" class="movie">
        <div class="cover">
          <el-image v-if="coverUrls[movie.id]" :src="coverUrls[movie.id]" fit="cover" /><span
            v-else
            >{{ movie.title.slice(0, 1) }}</span
          >
        </div>
        <div class="movie-copy">
          <div>
            <el-tag size="small" effect="plain">{{ statusName(movie.watchStatus) }}</el-tag
            ><span class="meta"
              >{{ movie.releaseYear }} · {{ movie.type }} · {{ movie.minutes }} 分钟</span
            >
          </div>
          <h2>{{ movie.title }}</h2>
          <p>{{ movie.description || '暂未填写影片简介' }}</p>
          <el-progress
            v-if="movie.watchStatus === 'WATCHING'"
            :percentage="movie.progress"
            :stroke-width="5"
            :show-text="false"
          />
        </div>
        <div class="movie-score">
          <strong v-if="movie.rating">{{ movie.rating }}</strong
          ><small v-if="movie.rating">评分</small>
          <div>
            <el-button link @click="open(movie)">编辑</el-button
            ><el-button link type="danger" @click="del(movie.id)">删除</el-button>
          </div>
        </div>
      </article>
    </div>
    <el-empty v-if="!loading && !rows.length" description="片单还是空的" /><el-dialog
      v-model="dialog"
      :title="editing ? '编辑影片' : '添加影片'"
      width="min(620px,94vw)"
      ><el-form label-position="top"
        ><el-form-item label="片名"><el-input v-model="form.title" /></el-form-item>
        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="类型"><el-input v-model="form.type" /></el-form-item
          ><el-form-item label="上映年份"
            ><el-input-number v-model="form.releaseYear" :min="1888" class="w-full" /></el-form-item
          ><el-form-item label="导演"><el-input v-model="form.director" /></el-form-item
          ><el-form-item label="时长（分钟）"
            ><el-input-number v-model="form.minutes" :min="1" class="w-full" /></el-form-item
          ><el-form-item label="评分"
            ><el-input-number
              v-model="form.rating"
              :min="0"
              :max="10"
              :step="0.1"
              class="w-full" /></el-form-item
          ><el-form-item label="观看状态"
            ><el-select v-model="form.watchStatus" class="w-full"
              ><el-option label="想看" value="PLANNED" /><el-option
                label="在看"
                value="WATCHING" /><el-option label="看过" value="FINISHED" /></el-select
          ></el-form-item>
        </div>
        <el-form-item v-if="form.watchStatus === 'WATCHING'" label="观看进度"
          ><el-slider v-model="form.progress" /></el-form-item
        ><el-form-item label="封面"
          ><el-upload :http-request="upload" :show-file-list="false" accept="image/*"
            ><el-button>{{ form.coverFileId ? '更换封面' : '上传封面' }}</el-button></el-upload
          ></el-form-item
        ><el-form-item label="简介"
          ><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.movie-list {
  display: grid;
  gap: 0;
  border-top: 1px solid var(--line);
}
.movie {
  display: grid;
  grid-template-columns: 84px 1fr 110px;
  gap: 22px;
  padding: 22px 0;
  border-bottom: 1px solid var(--line);
}
.cover {
  width: 84px;
  height: 116px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 10px;
  background: #dce5dd;
  color: #426157;
  font:
    34px Georgia,
    serif;
}
.cover :deep(.el-image) {
  width: 100%;
  height: 100%;
}
.movie-copy h2 {
  margin: 10px 0 6px;
  font:
    600 20px Georgia,
    'Songti SC',
    serif;
}
.movie-copy p {
  margin: 0 0 16px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.6;
}
.meta {
  margin-left: 10px;
  color: var(--muted);
  font-size: 12px;
}
.movie-score {
  text-align: right;
}
.movie-score > strong {
  display: block;
  font:
    600 30px Georgia,
    serif;
  color: var(--clay);
}
.movie-score > small {
  color: var(--muted);
}
.movie-score > div {
  margin-top: 25px;
}
@media (max-width: 600px) {
  .movie {
    grid-template-columns: 64px 1fr;
  }
  .cover {
    width: 64px;
    height: 90px;
  }
  .movie-score {
    grid-column: 2;
    text-align: left;
  }
  .movie-score > strong,
  .movie-score > small {
    display: none;
  }
  .movie-score > div {
    margin: 0;
  }
}
</style>
