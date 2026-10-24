<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Calendar, Check, List, MoreFilled, Plus, Refresh, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { dailyTasksApi } from '@/api/dailyTasks'
import { transactionsApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { MemberOption } from '@/types/api'
import type { TaskOccurrence, TaskPayload } from '@/types/dailyTask'
import { buildCalendarDays, formatTaskDate, moveCalendarMonth } from '@/utils/dailyTasks'
import { getDailyTaskMemberStyle } from '@/utils/dailyTaskColors'
import { resolveActiveFamilyId } from '@/utils/resource'
import TaskCalendar from '@/components/TaskCalendar.vue'
import DailyTaskForm from '@/components/DailyTaskForm.vue'

const authStore = useAuthStore()
const selectedDate = ref(formatTaskDate(new Date()))
const month = ref(selectedDate.value.slice(0, 7))
const viewMode = ref<'calendar' | 'list'>('calendar')
const filters = reactive({ memberId: '' as number | '', status: '' as '' | 'pending' | 'completed' })
const tasksData = ref<TaskOccurrence[]>([])
const membersData = ref<MemberOption[]>([])
const isLoading = ref(false)
const loadFailed = ref(false)
const isDialogVisible = ref(false)
const editingTaskData = ref<TaskOccurrence | null>(null)
const isSubmitting = ref(false)
const pendingKeys = reactive(new Set<string>())
const options = {
  statuses: [{ label: '全部状态', value: '' }, { label: '待完成', value: 'pending' }, { label: '已完成', value: 'completed' }],
}
let loadSequence = 0
let memberSequence = 0
const activeFamilyId = computed(() => authStore.user ? resolveActiveFamilyId(authStore.user, authStore.selectedFamilyId) : null)
const isAdmin = computed(() => authStore.user?.role === 'SUPER_ADMIN')
const memberOptions = computed(() => membersData.value)
const visibleTasksData = computed(() => tasksData.value.filter((task) => !filters.status || task.completed === (filters.status === 'completed')))
const selectedTasksData = computed(() => visibleTasksData.value.filter((task) => task.taskDate === selectedDate.value))
const selectedAllTasksData = computed(() => tasksData.value.filter((task) => task.taskDate === selectedDate.value))
const completedCount = computed(() => selectedAllTasksData.value.filter((task) => task.completed).length)
const progress = computed(() => selectedAllTasksData.value.length ? Math.round(completedCount.value / selectedAllTasksData.value.length * 100) : 0)
const selectedDateLabel = computed(() => `${Number(selectedDate.value.slice(5, 7))} 月 ${Number(selectedDate.value.slice(8))} 日 ${new Date(`${selectedDate.value}T12:00:00`).toLocaleDateString('zh-CN', { weekday: 'long' })}`)
const monthTasksData = computed(() => visibleTasksData.value.filter((task) => task.taskDate.startsWith(month.value)))

watch([activeFamilyId, () => authStore.user?.id], () => {
  ++memberSequence
  membersData.value = []
  tasksData.value = []
  filters.memberId = ''
  isDialogVisible.value = false
  if (activeFamilyId.value !== null) void apiLoadMembers()
  // 先让成员条件恢复默认，再发起当前家庭查询。
  void apiLoadTasks()
}, { immediate: true })
watch([month, () => filters.memberId], apiLoadTasks)

/** 成员选项仅使用当前家庭接口结果，切换家庭后丢弃旧响应。 */
async function apiLoadMembers(): Promise<void> {
  const tempSequence = ++memberSequence
  const res = await transactionsApi.members()
  if (tempSequence === memberSequence) membersData.value = res
}

/** 月历范围始终完整加载，状态在本地过滤，保留当天真实完成进度。 */
async function apiLoadTasks(): Promise<void> {
  const tempSequence = ++loadSequence
  tasksData.value = []
  loadFailed.value = false
  if (activeFamilyId.value === null) { isLoading.value = false; return }
  isLoading.value = true
  try {
    const tempDays = buildCalendarDays(month.value)
    const res = await dailyTasksApi.apiList({ from: tempDays[0], to: tempDays[tempDays.length - 1], memberId: filters.memberId === '' ? undefined : filters.memberId })
    if (tempSequence === loadSequence) tasksData.value = res
  } catch {
    // 请求层负责错误提示；这里只记录面板的失败状态，提供显式重试入口。
    if (tempSequence === loadSequence) loadFailed.value = true
  } finally {
    if (tempSequence === loadSequence) isLoading.value = false
  }
}

/** 切月后选中月初；相邻月日期点击仍可查看已加载的当天任务。 */
function handleMoveMonth(offset: number): void {
  month.value = moveCalendarMonth(month.value, offset)
  selectedDate.value = `${month.value}-01`
}
/** 回到本地当天并同步月份，避免停留在其他月的日期。 */
function handleToday(): void {
  selectedDate.value = formatTaskDate(new Date())
  month.value = selectedDate.value.slice(0, 7)
}
/** 双击日期或详情添加都使用目标日期，表单不会沿用上一次编辑内容。 */
function handleCreate(date = selectedDate.value): void {
  selectedDate.value = date
  editingTaskData.value = null
  isDialogVisible.value = true
}
/** 查看全家任务不扩大编辑、删除和打卡权限。 */
function canManageTask(task: TaskOccurrence): boolean {
  return isAdmin.value || task.memberId === authStore.user?.id
}
/** 表单使用任务原始开始日期，重复任务的编辑作用于整个任务。 */
function handleEdit(task: TaskOccurrence): void {
  editingTaskData.value = task
  isDialogVisible.value = true
}
async function handleSubmit(payload: TaskPayload): Promise<void> {
  if (isSubmitting.value || activeFamilyId.value === null) return
  const tempFamily = activeFamilyId.value
  isSubmitting.value = true
  try {
    if (editingTaskData.value) await dailyTasksApi.apiUpdate(editingTaskData.value.id, payload)
    else await dailyTasksApi.apiCreate(payload)
    if (tempFamily !== activeFamilyId.value) return
    isDialogVisible.value = false
    ElMessage.success('任务已保存')
    await apiLoadTasks()
  } finally { isSubmitting.value = false }
}

/** 同一天的打卡独立防重，并在切换家庭后忽略原操作的刷新。 */
async function handleToggle(task: TaskOccurrence): Promise<void> {
  const tempKey = `${task.id}:${task.taskDate}`
  if (pendingKeys.has(tempKey)) return
  const tempFamily = activeFamilyId.value
  pendingKeys.add(tempKey)
  try {
    await dailyTasksApi.apiComplete(task.id, task.taskDate, !task.completed)
    if (tempFamily === activeFamilyId.value) await apiLoadTasks()
  } finally { pendingKeys.delete(tempKey) }
}
/** 删除前明确重复范围，确认期间切换家庭时取消原操作。 */
async function handleRemove(task: TaskOccurrence): Promise<void> {
  const tempFamily = activeFamilyId.value
  const tempConfirmed = await ElMessageBox.confirm(task.repeatDaily ? `删除“${task.title}”及其所有日期的重复任务？` : `确定删除“${task.title}”？`, '删除任务', { type: 'warning' }).then(() => true, () => false)
  if (!tempConfirmed || tempFamily !== activeFamilyId.value) return
  await dailyTasksApi.apiRemove(task.id)
  if (tempFamily === activeFamilyId.value) { ElMessage.success('任务已删除'); await apiLoadTasks() }
}
</script>

<template>
  <section class="daily-tasks-page prototype-page">
    <div class="tasks-toolbar">
      <div class="task-filter"><label>成员</label><el-select v-model="filters.memberId" placeholder="全部成员" data-testid="task-member-filter"><el-option label="全部成员" value="" /><el-option v-for="member in memberOptions" :key="member.userId" :label="member.displayName" :value="member.userId" /></el-select></div>
      <div class="task-filter"><label>状态</label><el-select v-model="filters.status" placeholder="全部状态"><el-option v-for="option in options.statuses" :key="option.value" :label="option.label" :value="option.value" /></el-select></div>
      <div class="toolbar-actions"><el-radio-group v-model="viewMode" aria-label="任务视图"><el-radio-button value="calendar"><el-icon><calendar /></el-icon>日历</el-radio-button><el-radio-button value="list"><el-icon><list /></el-icon>列表</el-radio-button></el-radio-group><el-button type="primary" :icon="Plus" :disabled="activeFamilyId === null" @click="handleCreate()">新建任务</el-button></div>
    </div>
    <div v-if="activeFamilyId === null" class="card empty"><div class="empty-title">请先选择家庭</div><div class="empty-desc">选择家庭后查看每日任务</div></div>
    <template v-else>
      <div v-if="loadFailed" class="load-error" role="alert"><span>任务加载失败，请重试</span><el-button :icon="RefreshRight" size="small" @click="apiLoadTasks">重新加载</el-button></div>
      <div v-loading="isLoading" class="tasks-workspace">
        <task-calendar v-if="viewMode === 'calendar'" :month="month" :selected-date="selectedDate" :today="formatTaskDate(new Date())" :tasks="visibleTasksData" @select="selectedDate = $event" @create="handleCreate" @move="handleMoveMonth" @today="handleToday" />
        <section v-else class="card task-list-panel">
          <header class="list-heading"><h2>{{ month.replace('-', ' 年 ') }} 月任务</h2><div><el-button @click="handleMoveMonth(-1)">上月</el-button><el-button @click="handleToday">今天</el-button><el-button @click="handleMoveMonth(1)">下月</el-button></div></header>
          <div class="table-wrap"><table><thead><tr><th>日期</th><th>任务</th><th>成员</th><th>状态</th></tr></thead><tbody><tr v-for="task in monthTasksData" :key="`${task.id}:${task.taskDate}`" tabindex="0" @click="selectedDate = task.taskDate" @keydown.enter="selectedDate = task.taskDate"><td>{{ task.taskDate.slice(5) }}</td><td>{{ task.title }}</td><td>{{ task.memberName }}</td><td><span class="task-tag" :class="{ done: task.completed }">{{ task.completed ? '已完成' : '待完成' }}</span></td></tr></tbody></table></div>
          <div v-if="!monthTasksData.length && !isLoading" class="empty"><div class="empty-title">本月没有符合条件的任务</div></div>
        </section>
        <aside class="card task-details" aria-label="当天任务">
          <header class="details-heading"><h2>{{ selectedDateLabel }}</h2><div class="progress-description"><span>当天任务 {{ completedCount }} / {{ selectedAllTasksData.length }} 已完成</span><span>{{ progress }}%</span></div><div class="progress-track"><div :style="{ width: `${progress}%` }" /></div></header>
          <div class="details-body">
            <article v-for="task in selectedTasksData" :key="task.id" class="detail-task" :class="{ completed: task.completed }" :style="getDailyTaskMemberStyle(task.memberId)">
              <span class="member-dot" aria-hidden="true" />
              <button class="completion-toggle" :class="{ checked: task.completed }" :disabled="!canManageTask(task) || pendingKeys.has(`${task.id}:${task.taskDate}`)" :aria-label="`${task.completed ? '取消完成' : '完成'}：${task.title}`" :aria-pressed="task.completed" @click="handleToggle(task)"><el-icon v-if="task.completed"><check /></el-icon></button>
              <div class="detail-content"><h3 :title="task.title">{{ task.title }}</h3><p>{{ task.memberName }}<template v-if="task.taskTime"> · {{ task.taskTime.slice(0, 5) }}</template></p><p v-if="task.note" class="task-note">{{ task.note }}</p></div>
              <div class="detail-actions"><el-dropdown v-if="canManageTask(task)" trigger="click"><button class="task-menu" :aria-label="`${task.title}的更多操作`"><el-icon><more-filled /></el-icon></button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="handleEdit(task)">编辑{{ task.repeatDaily ? '重复任务' : '' }}</el-dropdown-item><el-dropdown-item divided @click="handleRemove(task)">删除{{ task.repeatDaily ? '重复任务' : '' }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown><span v-if="task.completed" class="task-tag done">完成</span><span v-else-if="task.repeatDaily" class="task-tag"><el-icon><refresh /></el-icon>每天</span><span v-else class="task-tag" :class="{ pending: !task.category }">{{ task.category || '待完成' }}</span></div>
            </article>
            <div v-if="!selectedTasksData.length && !isLoading && !loadFailed" class="task-empty"><el-icon><calendar /></el-icon><h3>这一天还没有{{ filters.status ? '符合条件的' : '' }}任务</h3><p>安排一件小事，让今天更有条理</p></div>
            <button class="add-day-task" @click="handleCreate()"><el-icon><plus /></el-icon>添加当天任务</button>
          </div>
        </aside>
      </div>
    </template>
    <daily-task-form v-if="authStore.user" v-model="isDialogVisible" :task="editingTaskData" :date="selectedDate" :current-user-id="authStore.user.id" :members="memberOptions" :submitting="isSubmitting" @submit="handleSubmit" />
  </section>
</template>

<style scoped>
.tasks-toolbar { display:flex; align-items:center; gap:20px; margin-bottom:16px; flex-wrap:wrap; }.task-filter { display:flex; align-items:center; gap:10px; }.task-filter label { color:var(--text-2); font-size:13px; }.task-filter :deep(.el-select) { width:150px; }
.toolbar-actions { display:flex; align-items:center; gap:12px; margin-left:auto; }.toolbar-actions :deep(.el-radio-button__inner) { display:flex; align-items:center; gap:7px; min-height:38px; padding:10px 18px; }.toolbar-actions :deep(.el-radio-button:first-child .el-radio-button__inner) { border-radius:9px 0 0 9px; }.toolbar-actions :deep(.el-radio-button:last-child .el-radio-button__inner) { border-radius:0 9px 9px 0; }
.tasks-workspace { display:grid; grid-template-columns:minmax(0,2.25fr) minmax(280px,1fr); gap:16px; align-items:stretch; }.task-details { min-width:0; padding:20px 18px; }.details-heading h2 { margin:0 0 20px; font-size:20px; font-weight:650; }.progress-description { display:flex; justify-content:space-between; font-size:12px; color:var(--text-2); margin-bottom:10px; }.progress-track { height:8px; border-radius:20px; background:#e9edf2; overflow:hidden; }.progress-track > div { height:100%; border-radius:20px; background:var(--primary); transition:width .2s; }.details-heading { padding-bottom:22px; border-bottom:1px solid var(--border); }
.detail-task { display:flex; gap:13px; padding:24px 0; border-bottom:1px solid var(--border); align-items:flex-start; }
.member-dot { flex:none; width:10px; height:10px; margin-top:8px; border-radius:50%; background:var(--task-member-accent); }
.completion-toggle { display:grid; place-items:center; width:20px; height:20px; flex:none; margin-top:3px; border:2px solid var(--task-member-accent); border-radius:50%; background:white; color:white; cursor:pointer; }
.completion-toggle.checked { background:var(--task-member-accent); }
.completion-toggle:disabled { cursor:not-allowed; opacity:.5; }
.detail-content { flex:1; min-width:0; }.detail-content h3 { font-size:15px; margin:0; line-height:1.6; overflow-wrap:anywhere; font-weight:600; }.detail-content p { color:var(--text-3); font-size:12px; margin:6px 0 0; }.completed h3 { text-decoration:line-through; color:var(--text-3); }.task-note { white-space:pre-wrap; overflow-wrap:anywhere; }
.detail-actions { display:flex; flex-direction:column; align-items:flex-end; justify-content:space-between; gap:14px; flex:none; }.task-menu { display:grid; place-items:center; border:none; background:transparent; color:var(--text-3); padding:3px; cursor:pointer; }.task-tag { display:inline-flex; align-items:center; gap:4px; font-size:11px; border-radius:5px; padding:3px 7px; color:var(--text-2); background:#f2f4f7; white-space:nowrap; }.task-tag.pending { background:#fff4e3; color:#946215; }.task-tag.done { background:#eaf5ee; color:#238367; }
.add-day-task { display:flex; align-items:center; justify-content:center; gap:9px; width:100%; min-height:54px; margin-top:24px; border:1px dashed #cbd9d1; border-radius:10px; background:transparent; color:var(--primary); font:inherit; font-size:13px; cursor:pointer; }.add-day-task .el-icon { font-size:20px; }.add-day-task:hover { background:var(--primary-weak); }.task-empty { text-align:center; padding:52px 0 25px; color:var(--text-3); }.task-empty > .el-icon { font-size:30px; color:#b8cbbf; }.task-empty h3 { font-size:13px; font-weight:500; }.task-empty p { font-size:12px; }
.task-list-panel { padding:20px; min-width:0; }.list-heading { display:flex; justify-content:space-between; gap:12px; margin-bottom:20px; flex-wrap:wrap; }.list-heading h2 { margin:0; font-size:20px; }.table-wrap td { cursor:pointer; overflow-wrap:anywhere; }.load-error { display:flex; align-items:center; gap:15px; margin-bottom:16px; color:#946215; background:#fff6e8; padding:10px 15px; border-radius:10px; font-size:13px; }
@media(min-width:1500px) { .tasks-workspace { grid-template-columns:minmax(0,2.6fr) minmax(320px,1fr); } }
@media(max-width:1050px) { .tasks-workspace { grid-template-columns:minmax(0,1fr); }.task-details { min-height:260px; }.tasks-toolbar { gap:12px; }.task-filter :deep(.el-select) { width:130px; }.toolbar-actions { gap:8px; } }
</style>
