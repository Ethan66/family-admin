<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { EditPen, Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { coursesApi } from '@/api/courses'
import { transactionsApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import { resolveActiveFamilyId } from '@/utils/resource'
import { formatTaskDate } from '@/utils/dailyTasks'
import { getCoursePeriod } from '@/utils/courses'
import type { MemberOption } from '@/types/api'
import type { CourseBalance, CourseColor, CourseOccurrence, CoursePayload, CourseRecord, CourseStatus } from '@/types/course'

const authStore = useAuthStore()
const today = ref(formatTaskDate(new Date()))
const activeTab = ref('week')
const memberId = ref<number | ''>('')
const membersData = ref<MemberOption[]>([])
const coursesData = ref<CourseOccurrence[]>([])
const recordsData = ref<CourseRecord[]>([])
const balancesData = ref<CourseBalance[]>([])
const isBalanceDialogVisible = ref(false)
const selectedBalanceData = ref<CourseBalance | null>(null)
const remainingLessons = ref<number | undefined>()
const totalLessons = ref<number | undefined>()
const historyFilters = reactive({ title: '', status: '' as '' | Exclude<CourseStatus, 'PENDING'>, month: null as string | null })
const recordTotal = ref(0)
const recordPage = ref(1)
const isLoading = ref(false)
const loadFailed = ref(false)
const isCourseDialogVisible = ref(false)
const isRecordDialogVisible = ref(false)
const isSubmitting = ref(false)
const isReadOnly = ref(false)
const editingCourseData = ref<CourseOccurrence | null>(null)
const selectedCourseData = ref<CourseOccurrence | null>(null)
const courseFormRef = ref<FormInstance>()
const courseForm = reactive<{ title: string; memberId: number | undefined; dayOfWeek: number; startTime: string; endTime: string; color: CourseColor }>({
  title: '', memberId: undefined, dayOfWeek: 1, startTime: '09:00', endTime: '10:00', color: 'blue',
})
const recordForm = reactive<{ date: string; title: string; time: string; status: Exclude<CourseStatus, 'PENDING'>; note: string }>({ date: '', title: '', time: '', status: 'ATTENDED', note: '' })
const options = {
  courses: ['跆拳道', '画画', '英语', '篮球'],
  recordStatuses: [{ label: '已上课', value: 'ATTENDED' }, { label: '请假', value: 'LEAVE' }],
  days: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
  periods: [{ value: 'morning', label: '上午' }, { value: 'afternoon', label: '下午' }],
  statuses: { PENDING: '待上课', ATTENDED: '已上课', LEAVE: '请假' },
  colors: [{ value: 'blue', label: '浅蓝' }, { value: 'purple', label: '浅紫' }, { value: 'green', label: '浅绿' }, { value: 'cyan', label: '浅青' }, { value: 'orange', label: '浅橙' }],
}
const courseRules: FormRules = {
  title: [{ required: true, whitespace: true, message: '请输入课程名称', trigger: 'blur' }],
  memberId: [{ required: true, message: '请选择家庭成员', trigger: 'change' }],
  dayOfWeek: [{ required: true, message: '请选择上课星期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
}
let loadSequence = 0
let memberSequence = 0
let refreshTimer: ReturnType<typeof setInterval> | undefined
let isAutoRefreshing = false
const activeFamilyId = computed(() => authStore.user ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId) : null)

watch([activeFamilyId, () => authStore.user?.id], () => {
  ++memberSequence
  membersData.value = []
  memberId.value = ''
  coursesData.value = []
  recordsData.value = []
  balancesData.value = []
  recordTotal.value = 0
  recordPage.value = 1
  isCourseDialogVisible.value = false
  isRecordDialogVisible.value = false
  isBalanceDialogVisible.value = false
  if (activeFamilyId.value !== null) void apiLoadMembers()
}, { immediate: true })
watch([activeFamilyId, () => authStore.user?.id, memberId, recordPage], () => { void apiLoadData() }, { immediate: true })
watch(memberId, () => { recordPage.value = 1 })
watch(activeTab, () => { recordPage.value = 1 })
watch([() => historyFilters.title, () => historyFilters.status, () => historyFilters.month], () => {
  if (recordPage.value !== 1) recordPage.value = 1
  else void apiLoadData()
})

function dateLabel(date: string): string {
  return `${date.slice(0, 4)}年${Number(date.slice(5, 7))}月${Number(date.slice(8))}日`
}
function shortDateLabel(date: string): string {
  return `${Number(date.slice(5, 7))}月${Number(date.slice(8))}日`
}
function timeLabel(start: string, end: string): string { return `${start.slice(0, 5)}–${end.slice(0, 5)}` }
function canWrite(course: { memberId: number }): boolean { return authStore.isSuperAdmin || course.memberId === authStore.user?.id }
/** 创建人可维护为其他成员添加的课程，上课记录权限仍单独判断。 */
function canEdit(course: CourseOccurrence): boolean { return canWrite(course) || course.createdBy === authStore.user?.id }
function dayCourses(day: number, period: string): CourseOccurrence[] {
  return coursesData.value.filter((course) => course.dayOfWeek === day && getCoursePeriod(course.startTime) === period)
}
/** 切换家庭或成员后旧请求不能覆盖当前页面，失败时提供重试入口。 */
async function apiLoadData(background = false): Promise<void> {
  const tempSequence = ++loadSequence
  if (!background) {
    coursesData.value = []
    recordsData.value = []
    balancesData.value = []
    recordTotal.value = 0
  }
  loadFailed.value = false
  if (activeFamilyId.value === null) { isLoading.value = false; return }
  if (!background) isLoading.value = true
  try {
    const res = await Promise.all([
      coursesApi.apiList({ memberId: memberId.value === '' ? undefined : memberId.value }),
      coursesApi.apiHistory({ page: recordPage.value, memberId: memberId.value === '' ? undefined : memberId.value,
        title: historyFilters.title === '' ? undefined : historyFilters.title,
        status: historyFilters.status === '' ? undefined : historyFilters.status,
        month: historyFilters.month === null ? undefined : historyFilters.month }),
      coursesApi.apiBalances({ memberId: memberId.value === '' ? undefined : memberId.value }),
    ])
    if (tempSequence !== loadSequence) return
    coursesData.value = res[0]
    recordsData.value = res[1].records
    recordTotal.value = res[1].total
    balancesData.value = res[2]
  } catch {
    // 通用错误提示由请求层负责，这里仅显示重试入口。
    if (tempSequence === loadSequence) loadFailed.value = true
  } finally {
    if (tempSequence === loadSequence) isLoading.value = false
  }
}
async function apiLoadMembers(): Promise<void> {
  const tempSequence = ++memberSequence
  const res = await transactionsApi.members()
  if (tempSequence === memberSequence) membersData.value = res
}
function handleCreate(): void {
  editingCourseData.value = null
  Object.assign(courseForm, { title: '', memberId: memberId.value === '' ? membersData.value.find((item) => item.userId === authStore.user?.id)?.userId : memberId.value,
    dayOfWeek: 1, startTime: '09:00', endTime: '10:00', color: 'blue' })
  isCourseDialogVisible.value = true
}
function handleEdit(course: CourseOccurrence): void {
  if (!canEdit(course)) return
  editingCourseData.value = course
  Object.assign(courseForm, { title: course.title, memberId: course.memberId, dayOfWeek: course.dayOfWeek,
    startTime: course.startTime.slice(0, 5), endTime: course.endTime.slice(0, 5), color: course.color })
  isCourseDialogVisible.value = true
}
async function handleSaveCourse(): Promise<void> {
  if (isSubmitting.value) return
  const tempFamilyId = activeFamilyId.value
  const tempUserId = authStore.user?.id
  const tempValid = await courseFormRef.value?.validate().catch(() => false)
  if (!tempValid || isSubmitting.value || courseForm.memberId === undefined || activeFamilyId.value !== tempFamilyId || authStore.user?.id !== tempUserId) return
  if (courseForm.startTime >= courseForm.endTime) { ElMessage.warning('结束时间必须晚于开始时间'); return }
  const tempBody: CoursePayload = { ...courseForm, title: courseForm.title.trim(), memberId: courseForm.memberId }
  isSubmitting.value = true
  try {
    if (editingCourseData.value) await coursesApi.apiUpdate(editingCourseData.value.id, tempBody)
    else await coursesApi.apiCreate(tempBody)
    if (activeFamilyId.value !== tempFamilyId) return
    isCourseDialogVisible.value = false
    ElMessage.success('课程已保存')
    await apiLoadData()
  } finally { isSubmitting.value = false }
}
async function handleDeleteCourse(): Promise<void> {
  const tempCourse = editingCourseData.value
  const tempFamilyId = activeFamilyId.value
  if (!tempCourse || isSubmitting.value) return
  const tempConfirmed = await ElMessageBox.confirm('删除后不再显示该课程的安排，历史上课记录会保留。', '删除课程', { type: 'warning' }).then(() => true, () => false)
  if (!tempConfirmed || isSubmitting.value || activeFamilyId.value !== tempFamilyId) return
  isSubmitting.value = true
  try {
    await coursesApi.apiRemove(tempCourse.id)
    if (activeFamilyId.value !== tempFamilyId) return
    isCourseDialogVisible.value = false
    ElMessage.success('课程已删除')
    await apiLoadData()
  } finally { isSubmitting.value = false }
}
function handleRecord(course: CourseOccurrence): void {
  selectedCourseData.value = course
  isReadOnly.value = !canEdit(course)
  Object.assign(recordForm, { date: course.date, title: course.title, time: timeLabel(course.startTime, course.endTime),
    status: course.date > today.value || course.status === 'LEAVE' ? 'LEAVE' : 'ATTENDED', note: course.note })
  isRecordDialogVisible.value = true
}
function handleViewRecord(record: CourseRecord): void {
  selectedCourseData.value = null
  isReadOnly.value = true
  Object.assign(recordForm, { date: record.courseDate, title: record.title, time: timeLabel(record.startTime, record.endTime), status: record.status, note: record.note })
  isRecordDialogVisible.value = true
}
async function handleSaveRecord(): Promise<void> {
  if (isSubmitting.value || !selectedCourseData.value || isReadOnly.value) return
  const tempFamilyId = activeFamilyId.value
  isSubmitting.value = true
  try {
    await coursesApi.apiRecord(selectedCourseData.value.id, { date: recordForm.date, status: recordForm.status, note: recordForm.note })
    if (activeFamilyId.value !== tempFamilyId) return
    isRecordDialogVisible.value = false
    recordPage.value = 1
    ElMessage.success('上课记录已保存')
    await apiLoadData()
  } finally { isSubmitting.value = false }
}
function handleEditBalance(balance: CourseBalance): void {
  if (!balance.editable) return
  selectedBalanceData.value = balance
  remainingLessons.value = balance.remainingLessons === null ? undefined : balance.remainingLessons
  totalLessons.value = balance.totalLessons === null ? undefined : balance.totalLessons
  isBalanceDialogVisible.value = true
}
async function handleSaveBalance(): Promise<void> {
  if (isSubmitting.value || !selectedBalanceData.value || remainingLessons.value === undefined) return
  if (remainingLessons.value < 0) { ElMessage.warning('剩余课时不能小于 0'); return }
  const tempFamilyId = activeFamilyId.value
  isSubmitting.value = true
  try {
    await coursesApi.apiSaveBalance({ memberId: selectedBalanceData.value.memberId, title: selectedBalanceData.value.title,
      remainingLessons: remainingLessons.value, totalLessons: totalLessons.value === undefined ? null : totalLessons.value })
    if (activeFamilyId.value !== tempFamilyId) return
    isBalanceDialogVisible.value = false
    ElMessage.success('剩余课时已保存')
    await apiLoadData()
  } finally { isSubmitting.value = false }
}
/** 静默刷新服务器自动记课结果，编辑弹窗内不刷新，跨午夜时同步当天日期。 */
async function handleAutoRefresh(): Promise<void> {
  const tempNow = new Date()
  if (tempNow.getHours() < 9 || tempNow.getHours() > 19 || (tempNow.getHours() === 19 && tempNow.getMinutes() > 0)
    || document.visibilityState === 'hidden' || isAutoRefreshing || isLoading.value || isSubmitting.value
    || isCourseDialogVisible.value || isRecordDialogVisible.value || isBalanceDialogVisible.value) return
  isAutoRefreshing = true
  today.value = formatTaskDate(new Date())
  try { await apiLoadData(true) } finally { isAutoRefreshing = false }
}
onMounted(() => { refreshTimer = setInterval(() => { void handleAutoRefresh() }, 60 * 60_000) })
onUnmounted(() => { clearInterval(refreshTimer); ++loadSequence; ++memberSequence })
</script>

<template>
  <div class="page-stack courses-page">
    <header class="course-toolbar">
      <el-tabs v-model="activeTab" class="course-tabs"><el-tab-pane label="每周课表" name="week" /><el-tab-pane label="上课记录" name="records" /></el-tabs>
      <div class="page-actions">
        <el-select v-model="memberId" aria-label="筛选家庭成员" placeholder="全部成员" style="width: 150px" :disabled="activeFamilyId === null">
          <el-option label="全部成员" value="" /><el-option v-for="member in membersData" :key="member.userId" :label="member.displayName" :value="member.userId" />
        </el-select>
        <el-button type="primary" :icon="Plus" :disabled="activeFamilyId === null || isSubmitting" @click="handleCreate">添加课程</el-button>
      </div>
    </header>
    <el-empty v-if="activeFamilyId === null" description="请先选择家庭" />
    <template v-else>
      <div v-if="loadFailed" class="load-error" role="alert">课程加载失败 <el-button @click="apiLoadData()">重新加载</el-button></div>
      <section v-if="activeTab === 'week'" v-loading="isLoading" class="surface schedule-panel" aria-label="每周课程表">
        <header class="week-toolbar">
          <div class="lesson-balances" aria-label="剩余课时">
            <div v-for="balance in balancesData" :key="`${balance.memberId}:${balance.title}`" class="lesson-balance">
              <span class="balance-subject">{{ balance.title }}<small v-if="memberId === ''"> · {{ balance.memberName }}</small></span>
              <span class="balance-value">剩余 <strong>{{ balance.remainingLessons === null ? '未设置' : balance.remainingLessons }}</strong><span v-if="balance.remainingLessons !== null"> 课时</span></span>
              <button v-if="balance.editable" class="balance-edit" type="button" :disabled="isSubmitting" :aria-label="`设置${balance.title}剩余课时`" @click="handleEditBalance(balance)"><el-icon><edit-pen /></el-icon></button>
            </div>
          </div>
          <div class="legend"><span><i class="attended" />已上课</span><span><i class="pending" />待上课</span><span><i class="leave" />请假</span></div>
        </header>
        <div class="schedule-scroll">
          <table class="schedule-table">
            <thead><tr><th class="period-heading" scope="col">时段</th><th v-for="day in 7" :key="day" class="day-heading" scope="col"><strong>{{ options.days[day - 1] }}</strong></th></tr></thead>
            <tbody><tr v-for="period in options.periods" :key="period.value">
              <th class="period-label" scope="row">{{ period.label }}</th>
              <td v-for="day in 7" :key="day">
                <div class="course-stack">
                  <article v-for="course in dayCourses(day, period.value)" :key="`${course.id}:${day}`" class="course-card" :class="`color-${course.color}`">
                    <div class="course-card-heading"><button class="course-name" type="button" :disabled="!canEdit(course) || isSubmitting" :aria-label="`编辑课程：${course.title}`" @click="handleEdit(course)">{{ course.title }}</button><span class="status-tag" :class="course.status.toLowerCase()">{{ options.statuses[course.status] }}</span></div>
                    <div class="course-time-row"><div class="course-time">{{ timeLabel(course.startTime, course.endTime) }}</div><button v-if="canEdit(course)" class="course-edit-button" type="button" :disabled="isSubmitting" :aria-label="`编辑课程：${course.title}`" @click="handleEdit(course)"><el-icon><edit-pen /></el-icon>编辑</button></div>
                    <div v-if="memberId === ''" class="course-member">{{ course.memberName }}</div>
                    <button v-if="canEdit(course)" class="record-button" type="button" :disabled="isSubmitting" @click="handleRecord(course)"><el-icon><edit-pen /></el-icon>{{ course.status === 'PENDING' ? (course.date > today ? '请假' : '记上课') : '修改记录' }}</button>
                  </article>
                </div>
              </td>
            </tr></tbody>
          </table>
        </div>
        <p v-if="!isLoading && !loadFailed && !coursesData.length" class="empty-week">暂无课程，点击“添加课程”开始安排</p>
        <p class="form-hint">每天 09:00–19:00 每小时整点检查，已结束的课程自动记为已上课并扣 1 课时，请提前标记请假。</p>
      </section>
      <section v-if="activeTab === 'records'" v-loading="isLoading" class="surface records-panel" aria-label="上课记录">
        <header class="records-heading"><h2>上课记录</h2>
          <div class="record-filters">
            <label class="filter-field"><span>课程</span><el-select v-model="historyFilters.title" aria-label="课程筛选" style="width:130px"><el-option label="全部课程" value="" /><el-option v-for="course in options.courses" :key="course" :label="course" :value="course" /></el-select></label>
            <label class="filter-field"><span>状态</span><el-select v-model="historyFilters.status" aria-label="状态筛选" style="width:120px"><el-option label="全部状态" value="" /><el-option v-for="status in options.recordStatuses" :key="status.value" :label="status.label" :value="status.value" /></el-select></label>
            <label class="filter-field"><span>月份</span><el-date-picker v-model="historyFilters.month" type="month" value-format="YYYY-MM" format="YYYY年MM月" placeholder="全部月份" aria-label="月份筛选" clearable style="width:155px" /></label>
          </div>
        </header>
        <el-table :data="recordsData" border empty-text="暂无上课记录" class="records-table">
          <el-table-column label="日期" min-width="140"><template #default="{ row }">{{ shortDateLabel(row.courseDate) }} <span class="weekday">{{ options.days[(new Date(`${row.courseDate}T12:00:00`).getDay() + 6) % 7] }}</span></template></el-table-column>
          <el-table-column prop="title" label="课程" min-width="100" />
          <el-table-column v-if="memberId === ''" prop="memberName" label="成员" min-width="85" />
          <el-table-column label="上课时间" min-width="150"><template #default="{ row }">{{ timeLabel(row.startTime, row.endTime) }}</template></el-table-column>
          <el-table-column label="剩余课时" min-width="125"><template #default="{ row }"><span class="lesson-ratio">{{ row.remainingLessons === null ? '—' : row.remainingLessons }}/{{ row.totalLessons === null ? '—' : row.totalLessons }}</span></template></el-table-column>
          <el-table-column label="状态" width="110"><template #default="{ row }"><span class="status-tag" :class="row.status.toLowerCase()">{{ options.statuses[row.status as CourseStatus] }}</span></template></el-table-column>
          <el-table-column prop="note" label="课堂备注" min-width="230" show-overflow-tooltip />
          <el-table-column label="操作" width="85"><template #default="{ row }"><el-button link type="primary" @click="handleViewRecord(row)">查看</el-button></template></el-table-column>
        </el-table>
        <el-pagination v-if="activeTab === 'records' && recordTotal > 20" v-model:current-page="recordPage" :page-size="20" :total="recordTotal" layout="prev, pager, next, total" class="record-pagination" />
      </section>
    </template>

    <el-dialog v-model="isBalanceDialogVisible" title="设置剩余课时" width="400px" destroy-on-close :close-on-click-modal="false">
      <p v-if="selectedBalanceData">{{ selectedBalanceData.memberName }} · {{ selectedBalanceData.title }}</p>
      <el-form label-position="top"><el-form-item label="剩余课时"><el-input-number v-model="remainingLessons" :min="0" :max="99999999" :precision="2" :step="1" :disabled="isSubmitting" aria-label="剩余课时" /></el-form-item><el-form-item label="总课时"><el-input-number v-model="totalLessons" :min="0" :max="99999999" :precision="2" :step="1" :disabled="isSubmitting" aria-label="总课时" /></el-form-item></el-form>
      <p class="form-hint">填写当前实际余额。之后每次记为已上课扣 1 课时，重复保存不重复扣减。</p>
      <template #footer><el-button :disabled="isSubmitting" @click="isBalanceDialogVisible = false">取消</el-button><el-button type="primary" :loading="isSubmitting" :disabled="remainingLessons === undefined" @click="handleSaveBalance">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="isCourseDialogVisible" :title="editingCourseData ? '编辑课程' : '添加课程'" width="500px" destroy-on-close :close-on-click-modal="false">
      <el-form ref="courseFormRef" :model="courseForm" :rules="courseRules" label-position="top" :disabled="isSubmitting">
        <el-form-item label="课程名称" prop="title"><el-input v-model="courseForm.title" maxlength="120" placeholder="例如：英语、钢琴" /></el-form-item>
        <el-form-item label="家庭成员" prop="memberId"><el-select v-model="courseForm.memberId" placeholder="请选择成员"><el-option v-for="member in membersData" :key="member.userId" :label="member.displayName" :value="member.userId" /></el-select></el-form-item>
        <el-form-item label="上课星期" prop="dayOfWeek"><el-select v-model="courseForm.dayOfWeek" placeholder="请选择上课星期"><el-option v-for="day in 7" :key="day" :label="options.days[day - 1]" :value="day" /></el-select></el-form-item>
        <div class="time-fields"><el-form-item label="开始时间" prop="startTime"><el-time-picker v-model="courseForm.startTime" format="HH:mm" value-format="HH:mm" :clearable="false" /></el-form-item><el-form-item label="结束时间" prop="endTime"><el-time-picker v-model="courseForm.endTime" format="HH:mm" value-format="HH:mm" :clearable="false" /></el-form-item></div>
        <el-form-item label="卡片颜色"><el-radio-group v-model="courseForm.color"><el-radio-button v-for="color in options.colors" :key="color.value" :value="color.value">{{ color.label }}</el-radio-button></el-radio-group></el-form-item>
        <p v-if="editingCourseData" class="form-hint">修改每周固定课程安排，已有上课记录保持不变。</p>
      </el-form>
      <template #footer><div class="dialog-footer"><el-button v-if="editingCourseData" type="danger" plain :disabled="isSubmitting" @click="handleDeleteCourse">删除课程</el-button><span class="footer-spacer" /><el-button :disabled="isSubmitting" @click="isCourseDialogVisible = false">取消</el-button><el-button type="primary" :loading="isSubmitting" @click="handleSaveCourse">保存</el-button></div></template>
    </el-dialog>
    <el-dialog v-model="isRecordDialogVisible" :title="isReadOnly ? '上课记录' : '记上课'" width="460px" destroy-on-close :close-on-click-modal="false">
      <div class="record-summary"><strong>{{ recordForm.title }}</strong><span>{{ dateLabel(recordForm.date) }} · {{ recordForm.time }}</span></div>
      <el-form label-position="top" :disabled="isReadOnly || isSubmitting"><el-form-item label="上课状态"><el-radio-group v-model="recordForm.status"><el-radio-button value="ATTENDED" :disabled="recordForm.date > today">已上课</el-radio-button><el-radio-button value="LEAVE">请假</el-radio-button></el-radio-group></el-form-item><el-form-item label="课堂备注"><el-input v-model="recordForm.note" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="记录课堂内容、学习情况等" /></el-form-item></el-form>
      <template #footer><el-button :disabled="isSubmitting" @click="isRecordDialogVisible = false">关闭</el-button><el-button v-if="!isReadOnly" type="primary" :loading="isSubmitting" @click="handleSaveRecord">保存记录</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-actions,.legend,.legend span,.records-heading,.dialog-footer { display:flex; align-items:center; }
.course-toolbar { display:flex; align-items:center; justify-content:space-between; gap:20px; border-bottom:1px solid var(--line); }.page-actions { flex:none; gap:12px; }.course-tabs { min-width:0; }.course-tabs :deep(.el-tabs__header) { margin:0; }.course-tabs :deep(.el-tabs__nav-wrap::after) { display:none; }.course-tabs :deep(.el-tabs__content) { display:none; }.course-tabs :deep(.el-tabs__item) { height:48px; font-size:16px; }
.schedule-panel,.records-panel { padding:20px; border-radius:14px; box-shadow:none; }.week-toolbar { display:flex; align-items:center; justify-content:space-between; gap:18px; margin-bottom:20px; flex-wrap:wrap; }
.lesson-balances { display:flex; align-items:center; flex-wrap:wrap; gap:12px 24px; }.lesson-balance { display:flex; align-items:center; gap:8px; }.balance-subject { font-size:14px; font-weight:600; }.balance-subject small { font-size:12px; font-weight:400; color:var(--muted); }.balance-value { font-size:12px; color:var(--muted); }.balance-value strong { color:var(--brand-deep); font-size:15px; font-variant-numeric:tabular-nums; }.balance-edit { display:flex; align-items:center; padding:3px; border:0; background:none; color:var(--brand); cursor:pointer; }.balance-edit:focus-visible { outline:2px solid var(--brand); outline-offset:2px; }
.legend { gap:20px; color:var(--muted); font-size:13px; }.legend span { gap:7px; }.legend i { width:10px; height:10px; border-radius:50%; }.legend .attended { background:#319768; }.legend .pending { background:#ee8b36; }.legend .leave { background:#9da4ab; }
.schedule-scroll { overflow-x:auto; border:1px solid var(--line); border-radius:10px; }.schedule-table { width:100%; min-width:900px; table-layout:fixed; border-collapse:collapse; }.schedule-table th,.schedule-table td { border-right:1px solid var(--line); border-bottom:1px solid var(--line); }.schedule-table tr > :last-child { border-right:0; }.schedule-table tbody tr:last-child > * { border-bottom:0; }
.schedule-table thead th { padding:14px 6px; background:#f7f9f8; font-weight:500; }.period-heading { width:76px; }.day-heading strong,.day-heading span { display:block; }.day-heading strong { margin-bottom:5px; font-size:15px; }
.period-label { font-weight:600; font-size:19px; background:#fafbfa; text-align:center; }.schedule-table td { height:210px; vertical-align:top; padding:16px 7px; }.course-stack { display:flex; flex-direction:column; gap:8px; }.course-card { padding:9px 10px; border:0; border-radius:9px; }.color-blue { background:#edf3fe; }.color-purple { background:#f2edfc; }.color-green { background:#eaf5ef; }.color-cyan { background:#e8f7fc; }.color-orange { background:#fff1e8; }
.course-card-heading { display:flex; align-items:flex-start; justify-content:space-between; flex-wrap:wrap; gap:7px; }.course-name { padding:0; border:0; background:none; font-size:15px; font-weight:600; text-align:left; color:inherit; cursor:pointer; overflow-wrap:anywhere; }.course-name:disabled { opacity:1; cursor:default; }.course-name:focus-visible,.record-button:focus-visible,.text-button:focus-visible { outline:2px solid var(--brand); outline-offset:3px; }.course-time-row { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:5px 8px; margin-top:7px; }.course-time { margin-top:0; font-size:14px; font-variant-numeric:tabular-nums; white-space:nowrap; }.course-member { margin-top:4px; color:var(--muted); font-size:12px; }
.status-tag { display:inline-flex; flex:none; align-items:center; padding:2px 7px; border:1px solid transparent; border-radius:7px; font-size:12px; font-weight:500; white-space:nowrap; }.status-tag.attended { color:#23764e; background:#effaf3; border-color:#bfdfcc; }.status-tag.pending { color:#c96820; background:#fff5ec; border-color:#f5d3b5; }.status-tag.leave { color:#68737b; background:#f1f3f5; border-color:#dce0e3; }
.course-edit-button { display:flex; align-items:center; gap:4px; margin-top:0; padding:0; border:0; background:none; color:var(--brand-deep); cursor:pointer; font-size:12px; }.course-edit-button:focus-visible { outline:2px solid var(--brand); outline-offset:3px; }.course-edit-button:disabled { opacity:.5; cursor:default; }
.record-button { display:flex; align-items:center; justify-content:center; gap:5px; width:100%; margin-top:10px; padding:5px; border:1px solid #dce6e4; border-radius:6px; background:#ffffffd9; color:var(--brand-deep); cursor:pointer; font-size:12px; }.empty-week { text-align:center; color:var(--muted); margin:18px 0 0; font-size:13px; }
.records-heading { justify-content:space-between; margin-bottom:16px; }.records-heading h2 { margin:0; font-size:19px; }.text-button { border:0; background:none; color:var(--brand); cursor:pointer; font-size:14px; }.records-table { border-radius:8px; --el-table-header-bg-color:#f5f7f6; --el-table-header-text-color:#526159; --el-table-row-hover-bg-color:#fafcfb; }.records-table :deep(.el-table__cell) { padding:13px 0; }.weekday { margin-left:7px; color:var(--muted); }.record-pagination { margin-top:18px; justify-content:flex-end; }
.records-heading { gap:16px; flex-wrap:wrap; }.record-filters { display:flex; align-items:center; gap:16px; flex-wrap:wrap; }.filter-field { display:flex; align-items:center; gap:8px; color:var(--muted); font-size:13px; }.lesson-ratio { font-variant-numeric:tabular-nums; color:var(--brand-deep); }
.time-fields { display:grid; grid-template-columns:1fr 1fr; gap:16px; }.time-fields :deep(.el-date-editor),.el-form-item :deep(.el-select),.el-form-item :deep(.el-date-editor) { width:100%; }.form-hint { color:var(--muted); font-size:12px; }.footer-spacer { flex:1; }.record-summary { display:flex; flex-direction:column; gap:8px; margin-bottom:22px; }.record-summary strong { font-size:18px; }.record-summary span { color:var(--muted); }.load-error { display:flex; align-items:center; gap:12px; color:#b96c24; }
@media(max-width:1100px) { .schedule-panel,.records-panel { padding:14px; } }
</style>
