<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examsApi } from '@/api/modules'
import type { Exam } from '@/types'
const rows = ref<Exam[]>([]),
  loading = ref(false),
  dialog = ref(false),
  reviewDialog = ref(false),
  editing = ref<number | null>(null),
  current = ref<Exam | null>(null)
const query = reactive({ subject: '', grade: '', status: '', page: 1, pageSize: 20 })
const form = reactive({
  name: '',
  subject: '数学',
  grade: '三年级',
  examDate: new Date().toISOString().slice(0, 10),
  fullScore: 100,
})
const review = reactive({ score: 0, reviewNote: '' })
async function load() {
  loading.value = true
  try {
    rows.value = (await examsApi.list(query)).records
  } finally {
    loading.value = false
  }
}
function open(row?: Exam) {
  editing.value = row?.id ?? null
  Object.assign(
    form,
    row || {
      name: '',
      subject: '数学',
      grade: '三年级',
      examDate: new Date().toISOString().slice(0, 10),
      fullScore: 100,
    },
  )
  dialog.value = true
}
async function save() {
  editing.value ? await examsApi.update(editing.value, form) : await examsApi.create(form)
  dialog.value = false
  ElMessage.success('试卷已保存')
  load()
}
function openReview(row: Exam) {
  current.value = row
  review.score = row.score || 0
  review.reviewNote = row.reviewNote || ''
  reviewDialog.value = true
}
async function submitReview() {
  if (!current.value) return
  await examsApi.review(current.value.id, review)
  reviewDialog.value = false
  ElMessage.success('批改已完成')
  load()
}
async function del(id: number) {
  await ElMessageBox.confirm('确认删除这份试卷？', '删除试卷', { type: 'warning' })
  await examsApi.remove(id)
  load()
}
onMounted(load)
</script>
<template>
  <section class="workspace">
    <header class="page-head">
      <div>
        <h1>试卷管理</h1>
        <p>保存每一次练习和进步</p>
      </div>
      <el-button type="primary" @click="open()">新增试卷</el-button>
    </header>
    <div class="toolbar">
      <el-input v-model="query.subject" clearable placeholder="科目" class="max-w-40" /><el-input
        v-model="query.grade"
        clearable
        placeholder="年级"
        class="max-w-40"
      /><el-select v-model="query.status" clearable placeholder="状态" class="w-36"
        ><el-option label="待批改" value="PENDING" /><el-option
          label="已批改"
          value="REVIEWED" /></el-select
      ><el-button @click="load">筛选</el-button>
    </div>
    <div class="surface">
      <el-table v-loading="loading" :data="rows"
        ><el-table-column prop="examDate" label="日期" width="120" /><el-table-column
          prop="name"
          label="试卷"
          min-width="200"
        /><el-table-column prop="subject" label="科目" /><el-table-column
          prop="grade"
          label="年级"
        /><el-table-column label="成绩"
          ><template #default="s"
            ><strong v-if="s.row.score != null">{{ s.row.score }} / {{ s.row.fullScore }}</strong
            ><span v-else class="text-gray-400">待批改</span></template
          ></el-table-column
        ><el-table-column label="状态"
          ><template #default="s"
            ><el-tag :type="s.row.status === 'REVIEWED' ? 'success' : 'info'">{{
              s.row.status === 'REVIEWED' ? '已批改' : '待批改'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="180" align="right"
          ><template #default="s"
            ><el-button link @click="openReview(s.row as Exam)">批改</el-button
            ><el-button link @click="open(s.row as Exam)">编辑</el-button
            ><el-button link type="danger" @click="del(s.row.id)">删除</el-button></template
          ></el-table-column
        ></el-table
      ><el-empty v-if="!loading && !rows.length" description="还没有试卷记录" />
    </div>
    <el-dialog v-model="dialog" :title="editing ? '编辑试卷' : '新增试卷'" width="min(540px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="试卷名称"><el-input v-model="form.name" /></el-form-item>
        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="科目"><el-input v-model="form.subject" /></el-form-item
          ><el-form-item label="年级"><el-input v-model="form.grade" /></el-form-item
          ><el-form-item label="考试日期"
            ><el-date-picker
              v-model="form.examDate"
              value-format="YYYY-MM-DD"
              class="w-full" /></el-form-item
          ><el-form-item label="满分"
            ><el-input-number v-model="form.fullScore" :min="1" class="w-full"
          /></el-form-item></div></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" @click="save">保存</el-button></template
      ></el-dialog
    ><el-dialog v-model="reviewDialog" title="批改试卷" width="min(480px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="得分"
          ><el-input-number
            v-model="review.score"
            :min="0"
            :max="current?.fullScore || 100"
            class="w-full" /></el-form-item
        ><el-form-item label="评语"
          ><el-input
            v-model="review.reviewNote"
            type="textarea"
            :rows="4" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="reviewDialog = false">取消</el-button
        ><el-button type="primary" @click="submitReview">完成批改</el-button></template
      ></el-dialog
    >
  </section>
</template>
