<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { transactionsApi } from '@/api/modules'
import type { Transaction } from '@/types'
const loading = ref(false),
  saving = ref(false),
  dialog = ref(false),
  rows = ref<Transaction[]>([]),
  total = ref(0),
  editing = ref<number | null>(null)
const query = reactive({
  month: new Date().toISOString().slice(0, 7),
  type: '',
  category: '',
  page: 1,
  pageSize: 20,
})
const summary = reactive({ income: 0, expense: 0, balance: 0 })
const form = reactive({
  type: 'EXPENSE',
  amount: 0,
  transactionDate: new Date().toISOString().slice(0, 10),
  category: '日常',
  memberName: '',
  note: '',
})
const money = (v: number) =>
  new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(v)
async function load() {
  loading.value = true
  try {
    const [list, sum] = await Promise.all([
      transactionsApi.list(query),
      transactionsApi.summary(query),
    ])
    rows.value = list.records
    total.value = list.total
    Object.assign(summary, sum)
  } finally {
    loading.value = false
  }
}
function open(row?: Transaction) {
  editing.value = row?.id ?? null
  Object.assign(
    form,
    row
      ? {
          type: row.type,
          amount: row.amount,
          transactionDate: row.transactionDate,
          category: row.category,
          memberName: row.memberName,
          note: row.note || '',
        }
      : {
          type: 'EXPENSE',
          amount: 0,
          transactionDate: new Date().toISOString().slice(0, 10),
          category: '日常',
          memberName: '',
          note: '',
        },
  )
  dialog.value = true
}
async function save() {
  saving.value = true
  try {
    editing.value
      ? await transactionsApi.update(editing.value, form)
      : await transactionsApi.create(form)
    ElMessage.success('已保存')
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
async function del(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，确认继续？', '删除记录', { type: 'warning' })
  await transactionsApi.remove(id)
  await load()
}
const hasRows = computed(() => rows.value.length > 0)
onMounted(load)
</script>
<template>
  <section class="workspace">
    <header class="page-head">
      <div>
        <h1>收入支出</h1>
        <p>按月份整理家庭每一笔往来</p>
      </div>
      <el-button type="primary" @click="open()">新增记录</el-button>
    </header>
    <div class="balance">
      <div>
        <small>收入</small><strong class="money-income">{{ money(summary.income) }}</strong>
      </div>
      <div>
        <small>支出</small><strong class="money-expense">{{ money(summary.expense) }}</strong>
      </div>
      <div>
        <small>结余</small><strong>{{ money(summary.balance) }}</strong>
      </div>
    </div>
    <div class="toolbar">
      <el-date-picker
        v-model="query.month"
        type="month"
        value-format="YYYY-MM"
        placeholder="选择月份"
        @change="load"
      /><el-select v-model="query.type" clearable placeholder="收支类型" class="w-32" @change="load"
        ><el-option label="收入" value="INCOME" /><el-option
          label="支出"
          value="EXPENSE" /></el-select
      ><el-input
        v-model="query.category"
        clearable
        placeholder="分类"
        class="max-w-48"
        @keyup.enter="load"
      /><el-button @click="load">筛选</el-button>
    </div>
    <div class="surface">
      <el-table v-if="hasRows" v-loading="loading" :data="rows"
        ><el-table-column prop="transactionDate" label="日期" width="120" /><el-table-column
          prop="type"
          label="类型"
          width="90"
          ><template #default="s"
            ><el-tag :type="s.row.type === 'INCOME' ? 'success' : 'warning'">{{
              s.row.type === 'INCOME' ? '收入' : '支出'
            }}</el-tag></template
          ></el-table-column
        ><el-table-column prop="category" label="分类" /><el-table-column
          prop="memberName"
          label="成员"
        /><el-table-column
          prop="note"
          label="说明"
          min-width="180"
          show-overflow-tooltip
        /><el-table-column label="金额" align="right"
          ><template #default="s"
            ><strong :class="s.row.type === 'INCOME' ? 'money-income' : 'money-expense'">{{
              money(s.row.amount)
            }}</strong></template
          ></el-table-column
        ><el-table-column label="操作" width="130" align="right"
          ><template #default="s"
            ><el-button link @click="open(s.row as Transaction)">编辑</el-button
            ><el-button link type="danger" @click="del(s.row.id)">删除</el-button></template
          ></el-table-column
        ></el-table
      ><el-empty v-else class="empty-space" description="这个月份还没有收支记录" />
    </div>
    <el-pagination
      v-if="total > query.pageSize"
      class="mt-5 justify-end"
      v-model:current-page="query.page"
      :page-size="query.pageSize"
      :total="total"
      @current-change="load"
    /><el-dialog v-model="dialog" :title="editing ? '编辑收支' : '新增收支'" width="min(520px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="类型"
          ><el-segmented
            v-model="form.type"
            :options="[
              { label: '支出', value: 'EXPENSE' },
              { label: '收入', value: 'INCOME' },
            ]"
        /></el-form-item>
        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="金额"
            ><el-input-number
              v-model="form.amount"
              :min="0.01"
              :precision="2"
              class="w-full" /></el-form-item
          ><el-form-item label="日期"
            ><el-date-picker
              v-model="form.transactionDate"
              value-format="YYYY-MM-DD"
              class="w-full"
          /></el-form-item>
        </div>
        <div class="grid grid-cols-2 gap-3">
          <el-form-item label="分类"><el-input v-model="form.category" /></el-form-item
          ><el-form-item label="家庭成员"><el-input v-model="form.memberName" /></el-form-item>
        </div>
        <el-form-item label="说明"
          ><el-input v-model="form.note" type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.balance {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 22px 0;
  border-block: 1px solid var(--line);
}
.balance > div {
  padding: 0 30px;
  border-right: 1px solid var(--line);
}
.balance > div:first-child {
  padding-left: 0;
}
.balance > div:last-child {
  border: 0;
}
.balance small {
  display: block;
  color: var(--muted);
}
.balance strong {
  display: block;
  margin-top: 8px;
  font:
    500 26px Georgia,
    serif;
}
@media (max-width: 600px) {
  .balance > div {
    padding: 0 10px;
  }
  .balance strong {
    font-size: 18px;
  }
}
</style>
