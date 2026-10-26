<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { transactionsApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { MemberOption, TransactionPayload, TransactionRecord, TransactionSummary } from '@/types/api'
import { canManageTransaction } from '@/utils/business'
import { resolveActiveFamilyId } from '@/utils/resource'

const authStore = useAuthStore()
const transactionsData = ref<TransactionRecord[]>([])
const summaryData = ref<TransactionSummary | null>(null)
const total = ref(0)
const isLoading = ref(false)
const isDialogVisible = ref(false)
const isSubmitting = ref(false)
const membersData = ref<MemberOption[]>([])
const isMembersLoading = ref(false)
const editingId = ref<number | null>(null)
const transactionFormRef = ref<FormInstance>()
const filters = reactive({ month: new Date().toLocaleDateString('sv-SE').slice(0, 7), type: '', memberName: '', category: '', bankCard: '', platform: '', page: 1, pageSize: 20 })
const transactionForm = reactive<TransactionPayload>({ type: 'EXPENSE', amount: 0, transactionDate: '', category: '', memberName: '', bankCard: null, platform: null, note: null })
const options = {
  filterTypes: [{ label: '全部', value: '' }, { label: '收入', value: 'INCOME' }, { label: '支出', value: 'EXPENSE' }],
  types: [{ label: '收入', value: 'INCOME' }, { label: '支出', value: 'EXPENSE' }],
  bankCards: ['工商', '农业', '交通', '光大', '招商', '支付宝', '微信'],
  platforms: ['支付宝', '微信', '京东', '美团', '拼多多', '淘宝', '抖音'],
  categories: ['餐饮', '交通', '教育', '住房', '医疗', '娱乐', '工资', '其他'],
}
const transactionRules: FormRules<TransactionPayload> = {
  type: [{ required: true, message: '请选择收支类型', trigger: 'change' }],
  amount: [{ required: true, type: 'number', min: 0.01, message: '金额必须大于 0', trigger: 'blur' }],
  transactionDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  memberName: [{ required: true, message: '请选择家庭成员', trigger: 'change' }],
}
const activeFamilyId = computed(() => authStore.user ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId) : null)
const canCreate = computed(() => activeFamilyId.value !== null)
const isAdmin = computed(() => authStore.user?.role === 'SUPER_ADMIN')
const filterMemberOptions = computed(() => {
  if (!authStore.user) return []
  if (!isAdmin.value) return [{ label: authStore.user.displayName, value: authStore.user.displayName }]
  const tempNames = new Set([authStore.user.displayName, ...membersData.value.map((member) => member.displayName)])
  return [{ label: '全部成员', value: '' }, ...[...tempNames].map((name) => ({ label: name, value: name }))]
})
const memberOptions = computed(() => {
  const tempNames = new Set<string>()
  if (authStore.user) tempNames.add(authStore.user.displayName)
  for (const tempMember of membersData.value) tempNames.add(tempMember.displayName)
  const tempOptions = [...tempNames].map((name) => ({ label: name, value: name, disabled: false }))
  if (editingId.value !== null && transactionForm.memberName && !tempNames.has(transactionForm.memberName)) {
    tempOptions.push({ label: transactionForm.memberName, value: transactionForm.memberName, disabled: true })
  }
  return tempOptions
})
const daysInMonth = computed(() => {
  const tempMonth = filters.month || new Date().toLocaleDateString('sv-SE').slice(0, 7)
  const [tempYear, tempMonthNumber] = tempMonth.split('-').map(Number)
  return new Date(tempYear, tempMonthNumber, 0).getDate()
})
const categoryTotals = computed(() => {
  const tempTotals = new Map<string, number>()
  for (const tempRecord of transactionsData.value) {
    if (tempRecord.type !== 'EXPENSE') continue
    tempTotals.set(tempRecord.category, (tempTotals.get(tempRecord.category) ?? 0) + Number(tempRecord.amount))
  }
  const tempTotal = [...tempTotals.values()].reduce((sum, value) => sum + value, 0)
  return [...tempTotals].sort((a, b) => b[1] - a[1]).map(([name, value], index) => ({ name, value, percentage: tempTotal ? value / tempTotal * 100 : 0, color: ['#236b4e', '#f0a02c', '#7a5af8', '#12a5a5'][index % 4] }))
})
let loadSequence = 0
let membersLoadSequence = 0

/** 弹窗按当前家庭刷新成员，过期响应不能覆盖切换家庭后的选项。 */
async function loadMembers(): Promise<void> {
  const tempSequence = ++membersLoadSequence
  membersData.value = []
  if (activeFamilyId.value === null) return
  isMembersLoading.value = true
  try {
    const res = await transactionsApi.members()
    if (tempSequence === membersLoadSequence) membersData.value = res
  } finally {
    if (tempSequence === membersLoadSequence) isMembersLoading.value = false
  }
}

function canManage(record: TransactionRecord): boolean {
  if (!authStore.user) return false
  return canManageTransaction(authStore.user.role, authStore.user.id, record.createdBy)
    || (authStore.user.role === 'FAMILY_MEMBER' && record.memberName !== '' && record.memberName === authStore.user.displayName)
}

function formatAmount(record: TransactionRecord): string {
  return `${record.type === 'INCOME' ? '+' : '-'}¥${Number(record.amount).toFixed(2)}`
}

function formatCurrency(value: number | undefined): string {
  return value === undefined ? '—' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value)
}

async function loadTransactions(): Promise<void> {
  const tempLoadSequence = ++loadSequence
  if (activeFamilyId.value === null) {
    transactionsData.value = []
    summaryData.value = null
    total.value = 0
    isLoading.value = false
    return
  }
  isLoading.value = true
  try {
    const tempParams = { month: filters.month || undefined, type: filters.type || undefined, memberName: filters.memberName || undefined, category: filters.category || undefined, bankCard: filters.bankCard || undefined, platform: filters.platform || undefined }
    const [res, summaryRes] = await Promise.all([
      transactionsApi.list({ ...tempParams, page: filters.page, pageSize: filters.pageSize }),
      transactionsApi.summary(tempParams),
    ])
    if (tempLoadSequence !== loadSequence) return
    transactionsData.value = res.records
    summaryData.value = summaryRes
    total.value = res.total
  } finally {
    if (tempLoadSequence === loadSequence) isLoading.value = false
  }
}

function resetTransactionForm(): void {
  if (!authStore.user) return
  Object.assign(transactionForm, { type: 'EXPENSE', amount: 0, transactionDate: new Date().toLocaleDateString('sv-SE'), category: '', memberName: authStore.user.displayName, bankCard: null, platform: null, note: null })
  editingId.value = null
}

/** 筛选条件变化后从第一页查询，避免停留在不存在的后续页。 */
async function handleSearch(): Promise<void> {
  filters.page = 1
  await loadTransactions()
}

function handleCreate(): void {
  resetTransactionForm()
  isDialogVisible.value = true
  void loadMembers()
}

function handleEdit(record: TransactionRecord): void {
  Object.assign(transactionForm, { type: record.type, amount: Number(record.amount), transactionDate: record.transactionDate, category: record.category, memberName: record.memberName, bankCard: record.bankCard, platform: record.platform, note: record.note })
  editingId.value = record.id
  isDialogVisible.value = true
  void loadMembers()
}

async function handleSubmit(): Promise<void> {
  const tempValid = await transactionFormRef.value?.validate().catch(() => false)
  if (!tempValid || isSubmitting.value) return
  isSubmitting.value = true
  try {
    if (editingId.value === null) await transactionsApi.create(transactionForm)
    else await transactionsApi.update(editingId.value, transactionForm)
    ElMessage.success('收支记录已保存')
    isDialogVisible.value = false
    await loadTransactions()
  } finally {
    isSubmitting.value = false
  }
}

async function handleRemove(record: TransactionRecord): Promise<void> {
  await ElMessageBox.confirm(`确定删除“${record.category}”这笔记录吗？`, '删除确认', { type: 'warning' })
  await transactionsApi.remove(record.id)
  ElMessage.success('已删除')
  await loadTransactions()
}

watch([activeFamilyId, () => authStore.user?.id, () => authStore.user?.displayName, isAdmin], () => {
  ++membersLoadSequence
  membersData.value = []
  isMembersLoading.value = false
  isDialogVisible.value = false
  filters.memberName = !isAdmin.value && authStore.user ? authStore.user.displayName : ''
  filters.page = 1
  void loadTransactions()
  if (isAdmin.value) void loadMembers()
}, { immediate: true })
</script>

<template>
  <section class="page-stack prototype-page">
    <template v-if="activeFamilyId !== null">
      <div class="toolbar">
        <div class="field"><label>月份</label><el-date-picker v-model="filters.month" type="month" value-format="YYYY-MM" placeholder="全部月份" clearable /></div>
        <div class="field"><label>类型</label><el-select v-model="filters.type"><el-option v-for="option in options.filterTypes" :key="option.value" :label="option.label" :value="option.value" /></el-select></div>
        <div class="field"><label>成员</label><el-select v-model="filters.memberName" data-testid="transaction-member-filter" placeholder="全部成员" :clearable="isAdmin"><el-option v-for="option in filterMemberOptions" :key="option.value" :label="option.label" :value="option.value" /></el-select></div>
        <div class="field"><label>分类</label><el-select v-model="filters.category" placeholder="全部分类" clearable><el-option v-for="category in options.categories" :key="category" :label="category" :value="category" /></el-select></div>
        <div class="field"><label>银行卡</label><el-select v-model="filters.bankCard" data-testid="transaction-bank-card-filter" placeholder="全部银行卡" clearable><el-option v-for="bankCard in options.bankCards" :key="bankCard" :label="bankCard" :value="bankCard" /></el-select></div>
        <div class="field"><label>消费平台</label><el-select v-model="filters.platform" data-testid="transaction-platform-filter" placeholder="全部平台" clearable><el-option v-for="platform in options.platforms" :key="platform" :label="platform" :value="platform" /></el-select></div>
        <el-button class="toolbar-end" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button v-if="canCreate" data-testid="create-transaction" type="primary" :icon="Plus" @click="handleCreate">新增记录</el-button>
      </div>
      <div class="stat-grid">
        <div class="card stat"><div class="stat-label"><i class="dot income-dot"></i>收入合计</div><div class="stat-value up">{{ formatCurrency(summaryData?.income) }}</div><div class="stat-foot">{{ summaryData?.incomeCount ?? 0 }} 笔</div></div>
        <div class="card stat"><div class="stat-label"><i class="dot expense-dot"></i>支出合计</div><div class="stat-value down">{{ formatCurrency(summaryData?.expense) }}</div><div class="stat-foot">{{ summaryData?.expenseCount ?? 0 }} 笔</div></div>
        <div class="card stat"><div class="stat-label"><i class="dot"></i>结余</div><div class="stat-value">{{ formatCurrency(summaryData?.balance) }}</div><div class="stat-foot">{{ filters.month || '全部区间' }}</div></div>
        <div class="card stat"><div class="stat-label"><i class="dot"></i>日均支出</div><div class="stat-value">{{ formatCurrency(summaryData ? summaryData.expense / daysInMonth : undefined) }}</div><div class="stat-foot">按 {{ daysInMonth }} 天计算</div></div>
      </div>
      <div class="grid-7-5">
        <div class="card"><div class="card-head"><div><h3>账目明细</h3><p>共 {{ total }} 笔</p></div></div><div class="card-body">
          <div v-loading="isLoading" class="table-wrap"><table><thead><tr><th>日期</th><th>类型</th><th>分类</th><th>银行卡</th><th>消费平台</th><th>备注</th><th class="right">金额</th><th class="right">操作</th></tr></thead><tbody>
            <tr v-for="record in transactionsData" :key="record.id"><td class="muted">{{ record.transactionDate }}</td><td><span class="tag" :class="record.type === 'INCOME' ? 'tag-income' : 'tag-expense'">{{ record.type === 'INCOME' ? '收入' : '支出' }}</span></td><td>{{ record.category }}</td><td>{{ record.bankCard || '—' }}</td><td>{{ record.platform || '—' }}</td><td><div class="cell-title">{{ record.note || '—' }}</div><div class="cell-sub">{{ record.memberName }}</div></td><td class="right"><strong class="amount" :class="record.type === 'INCOME' ? 'up' : 'down'">{{ formatAmount(record) }}</strong></td><td class="right"><div v-if="canManage(record)" class="row-actions"><button class="btn-text" @click="handleEdit(record)">编辑</button><button class="btn-text is-danger" @click="handleRemove(record)">删除</button></div><span v-else class="muted">只读</span></td></tr>
          </tbody></table><div v-if="!isLoading && !transactionsData.length" class="empty"><div class="empty-title">没有符合条件的记录</div><div class="empty-desc">换个筛选条件，或新增一笔试试</div></div></div>
          <el-pagination v-if="total > filters.pageSize" v-model:current-page="filters.page" class="pagination" :page-size="filters.pageSize" :total="total" layout="prev, pager, next" @current-change="loadTransactions" />
        </div></div>
        <div class="card"><div class="card-head"><div><h3>支出 TOP 分类</h3><p>当前页记录</p></div></div><div class="card-body"><div v-if="categoryTotals.length" class="bars"><div v-for="category in categoryTotals" :key="category.name" class="bar-row"><div class="bar-meta"><span class="name"><i :style="{ background: category.color }"></i>{{ category.name }}</span><span class="val">{{ formatCurrency(category.value) }} · {{ category.percentage.toFixed(1) }}%</span></div><div class="bar-track"><div class="bar-fill" :style="{ width: `${category.percentage}%`, background: category.color }"></div></div></div></div><div v-else class="empty"><div class="empty-title">暂无支出数据</div></div></div></div>
      </div>
    </template>
    <el-dialog v-model="isDialogVisible" :title="editingId === null ? '新增收支' : '编辑收支'" width="520px" destroy-on-close>
      <el-form ref="transactionFormRef" :model="transactionForm" :rules="transactionRules" label-position="top">
        <div class="form-grid"><el-form-item label="类型" prop="type"><el-radio-group v-model="transactionForm.type"><el-radio-button v-for="option in options.types" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button></el-radio-group></el-form-item><el-form-item label="金额" prop="amount"><el-input-number v-model="transactionForm.amount" :min="0.01" :precision="2" :step="10" controls-position="right" /></el-form-item></div>
        <div class="form-grid"><el-form-item label="日期" prop="transactionDate"><el-date-picker v-model="transactionForm.transactionDate" value-format="YYYY-MM-DD" type="date" placeholder="选择日期" /></el-form-item><el-form-item label="分类" prop="category"><el-select v-model="transactionForm.category" allow-create filterable><el-option v-for="category in options.categories" :key="category" :label="category" :value="category" /></el-select></el-form-item></div>
        <div class="form-grid">
          <el-form-item label="银行卡" prop="bankCard"><el-select v-model="transactionForm.bankCard" data-testid="transaction-bank-card" placeholder="请选择银行卡" clearable><el-option v-for="bankCard in options.bankCards" :key="bankCard" :label="bankCard" :value="bankCard" /></el-select></el-form-item>
          <el-form-item label="消费平台" prop="platform"><el-select v-model="transactionForm.platform" data-testid="transaction-platform" placeholder="请选择消费平台" clearable><el-option v-for="platform in options.platforms" :key="platform" :label="platform" :value="platform" /></el-select></el-form-item>
        </div>
        <el-form-item label="家庭成员" prop="memberName">
          <el-select v-model="transactionForm.memberName" data-testid="transaction-member" placeholder="请选择家庭成员" filterable style="width: 100%">
            <el-option v-for="option in memberOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" />
          </el-select>
          <span v-if="isMembersLoading" role="status" class="member-loading-hint">成员列表加载中…</span>
        </el-form-item><el-form-item label="备注" prop="note"><el-input v-model="transactionForm.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="isDialogVisible = false">取消</el-button><el-button type="primary" :loading="isSubmitting" @click="handleSubmit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.member-loading-hint { color:var(--el-text-color-secondary); font-size:12px; }
.pagination { justify-content:flex-end; margin-top:18px; }.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }.form-grid :deep(.el-input-number), .form-grid :deep(.el-date-editor), .form-grid :deep(.el-select) { width:100%; }
.income-dot { background:#e0484b !important; }.expense-dot { background:#17a34a !important; }
</style>
