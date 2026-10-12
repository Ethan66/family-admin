<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { dashboardApi, transactionsApi } from '@/api/modules'
import { loadResourceIndex } from '@/api/resources'
import { useAuthStore } from '@/stores/auth'
import type { DashboardOverview, TransactionRecord } from '@/types/api'
import type { ResourceEntry } from '@/types/resource'
import { resolveActiveFamilyId } from '@/utils/resource'

const authStore = useAuthStore()
const overviewData = ref<DashboardOverview | null>(null)
const recentTransactionsData = ref<TransactionRecord[]>([])
const monthlyTransactionsData = ref<TransactionRecord[]>([])
const isExpenseBreakdownPartial = ref(false)
const notesData = ref<ResourceEntry[]>([])
const resourceCountsData = ref({ photos: 0, movies: 0, exams: 0 })
const isLoading = ref(false)
const EXPENSE_COLORS = ['#3d6bf2', '#f0a02c', '#7a5af8', '#12a5a5', '#e0484b', '#17a34a', '#8a92a0']
const activeFamilyId = computed(() => authStore.user ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId) : null)
const expenseCategories = computed(() => {
  const tempTotals = new Map<string, number>()
  for (const tempRecord of monthlyTransactionsData.value) {
    if (tempRecord.type !== 'EXPENSE') continue
    tempTotals.set(tempRecord.category, (tempTotals.get(tempRecord.category) ?? 0) + Number(tempRecord.amount))
  }
  const tempExpenseTotal = [...tempTotals.values()].reduce((sum, value) => sum + value, 0)
  return [...tempTotals].sort((a, b) => b[1] - a[1]).map(([name, value], index) => ({
    name, value,
    percentage: tempExpenseTotal ? (value / tempExpenseTotal) * 100 : 0,
    color: EXPENSE_COLORS[index % EXPENSE_COLORS.length],
  }))
})
const expenseChartTotal = computed(() => expenseCategories.value.reduce((sum, category) => sum + category.value, 0))
/** 按原型的内外半径生成环形扇区；单一分类改用完整圆环，避免 360° 路径接缝。 */
const expensePieSegments = computed(() => {
  let tempAngle = -90
  return expenseCategories.value.map((category) => {
    const tempFraction = expenseChartTotal.value ? category.value / expenseChartTotal.value : 0
    if (tempFraction >= 0.9999) return { ...category, path: '', fullCircle: true }
    const tempStartAngle = tempAngle
    const tempEndAngle = tempStartAngle + tempFraction * 360
    tempAngle = tempEndAngle
    const tempRadians = Math.PI / 180
    const tempPoint = (radius: number, angle: number) => ({
      x: (100 + radius * Math.cos(angle * tempRadians)).toFixed(2),
      y: (100 + radius * Math.sin(angle * tempRadians)).toFixed(2),
    })
    const tempOuterStart = tempPoint(84, tempStartAngle)
    const tempOuterEnd = tempPoint(84, tempEndAngle)
    const tempInnerEnd = tempPoint(54, tempEndAngle)
    const tempInnerStart = tempPoint(54, tempStartAngle)
    const tempLargeArc = tempFraction > 0.5 ? 1 : 0
    return {
      ...category,
      fullCircle: false,
      path: `M${tempOuterStart.x} ${tempOuterStart.y} A84 84 0 ${tempLargeArc} 1 ${tempOuterEnd.x} ${tempOuterEnd.y} L${tempInnerEnd.x} ${tempInnerEnd.y} A54 54 0 ${tempLargeArc} 0 ${tempInnerStart.x} ${tempInnerStart.y} Z`,
    }
  })
})
const stats = computed(() => [
  { label: '本月收入', value: formatCurrency(overviewData.value?.income), foot: '本月家庭收入', tone: 'up', color: '#e0484b' },
  { label: '本月支出', value: formatCurrency(overviewData.value?.expense), foot: '本月家庭支出', tone: 'down', color: '#17a34a' },
  { label: '本月结余', value: formatCurrency(overviewData.value?.balance), foot: '收入减去支出', tone: '', color: '#236b4e' },
  { label: '试卷与影音', value: String(resourceCountsData.value.exams + resourceCountsData.value.photos + resourceCountsData.value.movies), foot: `试卷 ${resourceCountsData.value.exams} · 照片 ${resourceCountsData.value.photos} · 影片 ${resourceCountsData.value.movies}`, tone: '', color: '#7a5af8' },
])

function formatCurrency(value: number | undefined): string {
  return value === undefined ? '—' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(value)
}

/** 支出结构金额保留到分，避免小额分类被显示为 0 元。 */
function formatExpenseAmount(value: number): string {
  return `¥${new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)}`
}

async function loadDashboard(): Promise<void> {
  if (activeFamilyId.value === null) {
    overviewData.value = null
    recentTransactionsData.value = []
    monthlyTransactionsData.value = []
    isExpenseBreakdownPartial.value = false
    notesData.value = []
    resourceCountsData.value = { photos: 0, movies: 0, exams: 0 }
    return
  }
  isLoading.value = true
  try {
    const tempMonth = new Date().toLocaleDateString('sv-SE').slice(0, 7)
    const [overviewRes, indexRes, recentRes, monthlyRes] = await Promise.all([
      dashboardApi.overview(),
      loadResourceIndex(activeFamilyId.value),
      transactionsApi.list({ page: 1, pageSize: 5 }),
      transactionsApi.list({ month: tempMonth, page: 1, pageSize: 100 }),
    ])
    overviewData.value = overviewRes
    recentTransactionsData.value = recentRes.records
    monthlyTransactionsData.value = monthlyRes.records
    isExpenseBreakdownPartial.value = monthlyRes.total > monthlyRes.records.length
    notesData.value = indexRes.resources.filter((resource) => resource.kind === 'NOTE').sort((a, b) => b.modifiedAt.localeCompare(a.modifiedAt)).slice(0, 3)
    resourceCountsData.value = {
      photos: indexRes.resources.filter((resource) => resource.kind === 'PHOTO').length,
      movies: indexRes.resources.filter((resource) => resource.kind === 'MOVIE').length,
      exams: indexRes.resources.filter((resource) => resource.kind === 'EXAM').length,
    }
  } finally {
    isLoading.value = false
  }
}

watch(activeFamilyId, loadDashboard)
onMounted(loadDashboard)
</script>

<template>
  <section v-loading="isLoading" class="page-stack prototype-page">
    <div class="stat-grid">
      <div v-for="stat in stats" :key="stat.label" class="card stat">
        <div class="stat-label"><i class="dot" :style="{ background: stat.color }"></i>{{ stat.label }}</div>
        <div class="stat-value" :class="stat.tone">{{ stat.value }}</div>
        <div class="stat-foot">{{ stat.foot }}</div>
      </div>
    </div>
    <div class="grid-7-5">
      <div class="card">
        <div class="card-head"><div><h3>本月支出结构</h3><p>{{ isExpenseBreakdownPartial ? '按最近 100 条记录分类统计' : '按分类统计' }} · 共 {{ overviewData ? formatExpenseAmount(overviewData.expense) : '—' }}</p></div></div>
        <div class="card-body">
          <div v-if="expenseChartTotal > 0" class="pie-wrap">
            <div class="pie">
              <svg viewBox="0 0 200 200" width="188" height="188" role="img" aria-label="本月支出分类环形图">
                <template v-for="segment in expensePieSegments" :key="segment.name">
                  <circle v-if="segment.fullCircle" cx="100" cy="100" r="69" fill="none" :stroke="segment.color" stroke-width="30" />
                  <path v-else :d="segment.path" :fill="segment.color" />
                </template>
              </svg>
              <div class="pie-center">
                <span class="pc-label">本月支出</span>
                <span class="pc-value">{{ formatExpenseAmount(expenseChartTotal) }}</span>
                <span class="pc-sub">{{ expenseCategories.length }} 个分类</span>
              </div>
            </div>
            <div class="pie-legend">
              <div v-for="category in expenseCategories" :key="category.name" class="lg-row">
                <span class="lg-dot" :style="{ background: category.color }"></span>
                <span class="lg-name">{{ category.name }}</span>
                <span class="lg-val">{{ formatExpenseAmount(category.value) }}</span>
                <span class="lg-pct">{{ category.percentage.toFixed(1) }}%</span>
              </div>
            </div>
          </div>
          <div v-else class="empty"><div class="empty-title">暂无支出数据</div></div>
        </div>
      </div>
      <div class="stack">
        <div class="card">
          <div class="card-head"><div><h3>最近记账</h3><p>近 5 条记录</p></div><router-link class="card-link spacer" to="/transactions">查看全部</router-link></div>
          <div class="card-body"><div class="list">
            <div v-for="record in recentTransactionsData" :key="record.id" class="list-item">
              <div class="thumb">{{ record.category.slice(0, 1) }}</div>
              <div class="main"><div class="title">{{ record.note || record.category }}</div><div class="sub">{{ record.transactionDate }} · {{ record.category }}</div></div>
              <div class="amount" :class="record.type === 'INCOME' ? 'up' : 'down'">{{ record.type === 'INCOME' ? '+' : '-' }}{{ formatCurrency(record.amount) }}</div>
            </div>
            <div v-if="!recentTransactionsData.length" class="list-item"><div class="main"><div class="sub">还没有记账记录</div></div></div>
          </div></div>
        </div>
        <div class="card">
          <div class="card-head"><div><h3>最新笔记</h3><p>Markdown 学习笔记</p></div><router-link class="card-link spacer" to="/notes">进入笔记</router-link></div>
          <div class="card-body"><div class="list">
            <div v-for="note in notesData" :key="note.id" class="list-item"><div class="thumb">笔</div><div class="main"><div class="title">{{ note.name }}</div><div class="sub">{{ note.modifiedAt.slice(0, 10) }}</div></div><span class="tag tag-primary">{{ note.extension.slice(1).toUpperCase() }}</span></div>
            <div v-if="!notesData.length" class="list-item"><div class="main"><div class="sub">还没有笔记</div></div></div>
          </div></div>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.card-link { display:inline-flex; align-items:center; height:32px; padding:0 12px; border:1px solid var(--border-strong); border-radius:8px; color:var(--text-2); font-size:12px; text-decoration:none; }
.card-link:hover { background:var(--surface-sunken); }
.pie-wrap { display:flex; align-items:center; flex-wrap:wrap; gap:24px; }
.pie { position:relative; width:188px; height:188px; flex:0 0 auto; }
.pie svg { display:block; width:188px; height:188px; fill:none; stroke:none; }
.pie-center { position:absolute; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; pointer-events:none; }
.pc-label { color:var(--text-3); font-size:12px; }
.pc-value { margin-top:2px; color:var(--text); font-size:22px; font-weight:650; }
.pc-sub { margin-top:2px; color:var(--text-3); font-size:11px; }
.pie-legend { display:flex; flex:1 1 200px; flex-direction:column; gap:10px; min-width:180px; }
.lg-row { display:flex; align-items:center; gap:10px; font-size:13px; }
.lg-dot { width:10px; height:10px; flex:0 0 auto; border-radius:3px; }
.lg-name { color:var(--text-2); }
.lg-val { margin-left:auto; color:var(--text); font-weight:600; font-variant-numeric:tabular-nums; }
.lg-pct { width:48px; color:var(--text-3); text-align:right; font-variant-numeric:tabular-nums; }
</style>
