<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { passbooksApi } from '@/api/passbooks'
import { useAuthStore } from '@/stores/auth'
import { resolveActiveFamilyId } from '@/utils/resource'
import { canManageTransaction } from '@/utils/business'
import type { MemberOption } from '@/types/api'
import type { PassbookPayload, PassbookRecord, PassbookSort } from '@/types/passbook'

type PassbookSortColumn = 'DEPOSIT_DATE' | 'MATURITY_DATE'

const authStore = useAuthStore()
const recordsData = ref<PassbookRecord[]>([])
const membersData = ref<MemberOption[]>([])
const total = ref(0)
const summaryData = reactive({ totalPrincipal: 0, totalAmount: 0 })
const isLoading = ref(false)
const isMembersLoading = ref(false)
const isDialogVisible = ref(false)
const isSubmitting = ref(false)
const editingData = ref<PassbookRecord | null>(null)
const formRef = ref<FormInstance>()
const filters = reactive({ memberId: '' as number | '', depositYear: null as string | null, sort: 'DEPOSIT_DATE_DESC' as PassbookSort, page: 1, pageSize: 20 })
const form = reactive<Omit<PassbookPayload, 'memberId'> & { memberId: number | undefined }>({
  memberId: undefined, bank: '农商', depositDate: '', amount: 0, termYears: 1, interestRate: 0, totalInterest: 0, note: null,
})
const options = { banks: ['农商'] }
const rules: FormRules = {
  memberId: [{ required: true, message: '请选择成员', trigger: 'change' }],
  depositDate: [{ required: true, message: '请选择存款时间', trigger: 'change' }],
  amount: [{ required: true, type: 'number', min: 0.01, max: 999999999999.99, message: '请输入大于 0 的金额', trigger: 'blur' }],
  termYears: [{ required: true, type: 'integer', min: 1, max: 100, message: '年限须为 1 至 100 的整数', trigger: 'blur' }],
  interestRate: [{ required: true, type: 'number', min: 0, max: 100, message: '请输入 0 至 100 的年利率', trigger: 'blur' }],
  totalInterest: [{ required: true, type: 'number', min: 0, max: 999999999999.99, message: '请输入不小于 0 的总利息', trigger: 'blur' }],
}
let loadSequence = 0
let memberSequence = 0
const activeFamilyId = computed(() => authStore.user ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId) : null)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / filters.pageSize)))
const memberOptions = computed(() => {
  const tempOptions = membersData.value.map((tempMember) => ({ value: tempMember.userId, label: tempMember.displayName, disabled: false }))
  if (editingData.value && !tempOptions.some((tempOption) => tempOption.value === editingData.value!.memberId)) {
    tempOptions.push({ value: editingData.value.memberId, label: editingData.value.memberName, disabled: true })
  }
  return tempOptions
})
const maturityDate = computed(() => {
  if (!form.depositDate || !Number.isInteger(form.termYears) || form.termYears < 1) return '—'
  const [tempYear, tempMonth, tempDay] = form.depositDate.split('-').map(Number)
  const tempTargetYear = tempYear + form.termYears
  const tempLastDay = new Date(Date.UTC(tempTargetYear, tempMonth, 0)).getUTCDate()
  return `${tempTargetYear}-${String(tempMonth).padStart(2, '0')}-${String(Math.min(tempDay, tempLastDay)).padStart(2, '0')}`
})
const totalAmount = computed(() => (Math.round(Number(form.amount) * 100) + Math.round(Number(form.totalInterest) * 100)) / 100)

watch([activeFamilyId, () => authStore.user?.id], () => {
  ++loadSequence
  ++memberSequence
  recordsData.value = []
  membersData.value = []
  total.value = 0
  summaryData.totalPrincipal = 0
  summaryData.totalAmount = 0
  isDialogVisible.value = false
  editingData.value = null
  filters.memberId = ''
  filters.depositYear = null
  filters.sort = 'DEPOSIT_DATE_DESC'
  filters.page = 1
  isLoading.value = false
  isMembersLoading.value = false
  if (activeFamilyId.value !== null) {
    void apiLoadMembers()
    void apiLoadRecords()
  }
}, { immediate: true })

async function apiLoadMembers(): Promise<void> {
  const tempSequence = ++memberSequence
  isMembersLoading.value = true
  try {
    const res = await passbooksApi.apiMembers()
    if (tempSequence === memberSequence) membersData.value = res
  } finally {
    if (tempSequence === memberSequence) isMembersLoading.value = false
  }
}

async function apiLoadRecords(): Promise<void> {
  if (activeFamilyId.value === null) return
  const tempSequence = ++loadSequence
  isLoading.value = true
  try {
    const res = await passbooksApi.apiList({
      memberId: filters.memberId === '' ? undefined : filters.memberId,
      startDate: filters.depositYear ? `${filters.depositYear}-01-01` : undefined,
      endDate: filters.depositYear ? `${filters.depositYear}-12-31` : undefined,
      sort: filters.sort, page: filters.page, pageSize: filters.pageSize,
    })
    if (tempSequence !== loadSequence) return
    recordsData.value = res.records
    total.value = res.total
    summaryData.totalPrincipal = res.totalPrincipal
    summaryData.totalAmount = res.totalAmount
  } finally {
    if (tempSequence === loadSequence) isLoading.value = false
  }
}

async function handleSearch(): Promise<void> {
  filters.page = 1
  await apiLoadRecords()
}

/** 表头点击只改变全量查询的排序，并从第一页加载。 */
async function handleSort(column: PassbookSortColumn): Promise<void> {
  const tempAscending: PassbookSort = `${column}_ASC`
  const tempDescending: PassbookSort = `${column}_DESC`
  filters.sort = filters.sort === tempAscending ? tempDescending : tempAscending
  await handleSearch()
}

function getSortDirection(column: PassbookSortColumn): 'ascending' | 'descending' | 'none' {
  if (filters.sort === `${column}_ASC`) return 'ascending'
  if (filters.sort === `${column}_DESC`) return 'descending'
  return 'none'
}

function handleCreate(): void {
  editingData.value = null
  Object.assign(form, {
    memberId: membersData.value.some((tempMember) => tempMember.userId === authStore.user?.id) ? authStore.user!.id : undefined,
    bank: '农商', depositDate: new Date().toLocaleDateString('sv-SE'), amount: 0, termYears: 1, interestRate: 0, totalInterest: 0, note: null,
  })
  isDialogVisible.value = true
}

function handleEdit(record: PassbookRecord): void {
  editingData.value = record
  Object.assign(form, { memberId: record.memberId, bank: record.bank, depositDate: record.depositDate, amount: Number(record.amount),
    termYears: record.termYears, interestRate: Number(record.interestRate), totalInterest: Number(record.totalInterest), note: record.note })
  isDialogVisible.value = true
}

/** 家庭切换后，旧请求的完成状态不能关闭新家庭的弹窗或刷新其列表。 */
async function handleSubmit(): Promise<void> {
  const tempValid = await formRef.value?.validate().catch(() => false)
  if (!tempValid || isSubmitting.value || form.memberId === undefined) return
  const tempFamily = activeFamilyId.value
  const tempUserId = authStore.user?.id
  if (tempFamily === null) return
  const tempPayload: PassbookPayload = { ...form, memberId: form.memberId }
  isSubmitting.value = true
  try {
    if (editingData.value) await passbooksApi.apiUpdate(editingData.value.id, tempPayload)
    else await passbooksApi.apiCreate(tempPayload)
    if (tempFamily !== activeFamilyId.value || tempUserId !== authStore.user?.id) return
    ElMessage.success('存折记录已保存')
    isDialogVisible.value = false
    await apiLoadRecords()
  } finally {
    isSubmitting.value = false
  }
}

async function handleRemove(record: PassbookRecord): Promise<void> {
  const tempFamily = activeFamilyId.value
  const tempUserId = authStore.user?.id
  const tempConfirmed = await ElMessageBox.confirm('确定删除这笔存款记录吗？', '删除确认', { type: 'warning' }).then(() => true, () => false)
  if (!tempConfirmed || tempFamily !== activeFamilyId.value || tempUserId !== authStore.user?.id) return
  await passbooksApi.apiRemove(record.id)
  if (tempFamily !== activeFamilyId.value || tempUserId !== authStore.user?.id) return
  ElMessage.success('已删除')
  if (recordsData.value.length === 1 && filters.page > 1) filters.page -= 1
  await apiLoadRecords()
}

function canManage(record: PassbookRecord): boolean {
  return authStore.user !== null && canManageTransaction(authStore.user.role, authStore.user.id, record.createdBy)
}

function formatMoney(value: number, fractionDigits = 2): string {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: fractionDigits, maximumFractionDigits: fractionDigits }).format(value)
}

/** 以万为单位展示，最多六位小数可保留原金额的分，不补无意义的零。 */
function formatWan(value: number, unitSeparator = ' '): string {
  return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 6 }).format(value / 10000)}${unitSeparator}万`
}

onUnmounted(() => { ++loadSequence; ++memberSequence })
</script>

<template>
  <section class="page-stack prototype-page">
    <template v-if="activeFamilyId !== null">
      <div class="toolbar">
        <div class="field"><label>成员</label><el-select v-model="filters.memberId" placeholder="全部成员" clearable :loading="isMembersLoading"><el-option v-for="member in membersData" :key="member.userId" :label="member.displayName" :value="member.userId" /></el-select></div>
        <div class="field"><label>存款时间</label><el-date-picker v-model="filters.depositYear" data-testid="passbook-year-filter" type="year" value-format="YYYY" placeholder="全部年份" clearable /></div>
        <el-button :icon="Search" @click="handleSearch">查询</el-button>
        <el-button type="primary" class="toolbar-end" :icon="Plus" @click="handleCreate">新增存款</el-button>
      </div>
      <div class="card">
        <div class="card-body">
          <div v-loading="isLoading" class="table-wrap"><table><thead><tr><th>成员</th><th :aria-sort="getSortDirection('DEPOSIT_DATE')"><button type="button" class="sort-button" data-testid="passbook-sort-deposit" title="点击切换存款时间排序" @click="handleSort('DEPOSIT_DATE')">存款时间<span class="sort-arrows" aria-hidden="true"><span :class="{ active: filters.sort === 'DEPOSIT_DATE_ASC' }">▲</span><span :class="{ active: filters.sort === 'DEPOSIT_DATE_DESC' }">▼</span></span></button></th><th>银行</th><th>年份</th><th>利率</th><th :aria-sort="getSortDirection('MATURITY_DATE')"><button type="button" class="sort-button" data-testid="passbook-sort-maturity" title="点击切换到期时间排序" @click="handleSort('MATURITY_DATE')">到期时间<span class="sort-arrows" aria-hidden="true"><span :class="{ active: filters.sort === 'MATURITY_DATE_ASC' }">▲</span><span :class="{ active: filters.sort === 'MATURITY_DATE_DESC' }">▼</span></span></button></th><th class="right">金额</th><th class="right">总利息</th><th class="right">总金额</th><th>备注</th><th class="right">操作</th></tr></thead>
            <tbody><tr v-for="record in recordsData" :key="record.id"><td>{{ record.memberName }}</td><td>{{ record.depositDate }}</td><td>{{ record.bank || '—' }}</td><td>{{ record.termYears }} 年</td><td>{{ Number(record.interestRate) }}%</td><td>{{ record.maturityDate }}</td><td class="right">{{ formatWan(record.amount) }}</td><td class="right">{{ formatMoney(record.totalInterest) }}</td><td class="right"><strong>{{ formatWan(record.totalAmount) }}</strong></td><td class="note-cell">{{ record.note || '—' }}</td><td class="right"><div v-if="canManage(record)" class="row-actions"><button class="btn-text" @click="handleEdit(record)">编辑</button><button class="btn-text is-danger" @click="handleRemove(record)">删除</button></div><span v-else class="muted">只读</span></td></tr></tbody>
          </table><div v-if="!isLoading && !recordsData.length" class="empty"><div class="empty-title">没有符合条件的存款记录</div><div class="empty-desc">调整筛选条件，或新增一笔存款</div></div></div>
          <div class="list-footer">
            <div data-testid="passbook-totals" class="footer-totals" aria-live="polite">总本金：{{ formatWan(summaryData.totalPrincipal, '') }}，总金额：{{ formatWan(summaryData.totalAmount, '') }}</div>
            <div class="page-controls">
              <span data-testid="passbook-page-info" class="page-info">第 {{ filters.page }} / {{ pageCount }} 页 · 共 {{ total }} 笔</span>
              <el-pagination v-model:current-page="filters.page" :page-size="filters.pageSize" :total="total" layout="prev, pager, next" @current-change="apiLoadRecords" />
            </div>
          </div>
        </div>
      </div>
    </template>
    <el-empty v-else description="请先选择家庭，再查看存折" />
    <el-dialog v-model="isDialogVisible" :title="editingData ? '编辑存款' : '新增存款'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="form-grid">
          <el-form-item label="成员" prop="memberId"><el-select v-model="form.memberId" placeholder="请选择成员" :loading="isMembersLoading"><el-option v-for="option in memberOptions" :key="option.value" :label="option.label" :value="option.value" :disabled="option.disabled" /></el-select></el-form-item>
          <el-form-item label="银行" prop="bank"><el-select v-model="form.bank" data-testid="passbook-bank" placeholder="请选择银行" clearable><el-option v-for="bank in options.banks" :key="bank" :label="bank" :value="bank" /></el-select></el-form-item>
          <el-form-item label="存款时间" prop="depositDate"><el-date-picker v-model="form.depositDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="金额（元）" prop="amount"><el-input-number v-model="form.amount" :min="0.01" :max="999999999999.99" :precision="2" controls-position="right" /></el-form-item>
          <el-form-item label="存款年限（年）" prop="termYears"><el-input-number v-model="form.termYears" :min="1" :max="100" :precision="0" controls-position="right" /></el-form-item>
          <el-form-item label="年利率（%）" prop="interestRate"><el-input-number v-model="form.interestRate" :min="0" :max="100" :precision="4" :step="0.1" controls-position="right" /></el-form-item>
          <el-form-item label="总利息（元，手动填写）" prop="totalInterest"><el-input-number v-model="form.totalInterest" :min="0" :max="999999999999.99" :precision="2" controls-position="right" /></el-form-item>
        </div>
        <el-form-item label="备注" prop="note"><el-input v-model="form.note" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="选填，最多 500 字" /></el-form-item>
        <div class="deposit-preview"><span>到期时间 <strong>{{ maturityDate }}</strong></span><span>总金额 <strong>{{ formatMoney(totalAmount) }}</strong></span></div>
      </el-form>
      <template #footer><el-button @click="isDialogVisible = false">取消</el-button><el-button type="primary" :loading="isSubmitting" @click="handleSubmit">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.card { min-width:0; }
.prototype-page .toolbar { align-items:flex-end; }
.prototype-page .toolbar :deep(.el-date-editor.el-input) { height:38px; }
.table-wrap table { min-width:850px; }
.table-wrap td { white-space:nowrap; }
.list-footer { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:16px; margin-top:18px; }
.page-controls { margin-left:auto; justify-content:flex-end; display:flex; flex-wrap:wrap; align-items:center; gap:16px; }
.page-info { color:var(--text-3); font-size:13px; white-space:nowrap; }
.footer-totals { color:var(--text-2); font-size:14px; font-weight:400; font-variant-numeric:tabular-nums; white-space:nowrap; }
.sort-button { display:inline-flex; align-items:center; gap:5px; padding:0; border:0; background:transparent; color:inherit; font:inherit; cursor:pointer; }
.sort-button:hover { color:var(--primary); }
.sort-button:focus-visible { outline:2px solid var(--primary); outline-offset:4px; border-radius:2px; }
.sort-arrows { display:flex; flex-direction:column; color:var(--text-3); font-size:8px; line-height:8px; }
.sort-arrows .active { color:var(--primary); }
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 14px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number), .form-grid :deep(.el-date-editor) { width:100%; }
.table-wrap .note-cell { min-width:180px; max-width:320px; white-space:normal; overflow-wrap:anywhere; }
.deposit-preview { display:flex; flex-wrap:wrap; justify-content:space-between; gap:12px; padding:14px; border-radius:8px; background:var(--surface-weak); color:var(--text-3); }
.deposit-preview strong { margin-left:8px; color:var(--text); }
</style>
