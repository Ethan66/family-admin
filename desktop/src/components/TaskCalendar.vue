<script setup lang="ts">
import { computed } from 'vue'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import type { TaskOccurrence } from '@/types/dailyTask'
import { buildCalendarDays } from '@/utils/dailyTasks'
import { getDailyTaskMemberStyle } from '@/utils/dailyTaskColors'

const MAX_VISIBLE_TASKS = 3
const props = defineProps<{ month: string; selectedDate: string; today: string; tasks: TaskOccurrence[] }>()
const emit = defineEmits<{ select: [date: string]; create: [date: string]; move: [offset: number]; today: [] }>()
const days = computed(() => buildCalendarDays(props.month))
const monthLabel = computed(() => `${Number(props.month.slice(0, 4))} 年 ${Number(props.month.slice(5))} 月`)
const tasksByDay = computed(() => {
  const tempMap = new Map<string, TaskOccurrence[]>()
  for (const tempTask of props.tasks) {
    if (!tempMap.has(tempTask.taskDate)) tempMap.set(tempTask.taskDate, [])
    tempMap.get(tempTask.taskDate)!.push(tempTask)
  }
  // 仅排序新建的每日预览数组，不改变父页面列表和详情的任务顺序。
  for (const tempTasks of tempMap.values()) {
    tempTasks.sort((tempLeft, tempRight) => Number(tempLeft.completed) - Number(tempRight.completed))
  }
  return tempMap
})
/** 空日期也返回稳定数组，日历格同时读取预览与剩余数量。 */
function getDayTasks(date: string): TaskOccurrence[] {
  return tasksByDay.value.get(date) ?? []
}
</script>

<template>
  <section class="task-calendar card" aria-label="任务月历">
    <header class="calendar-heading">
      <h2>{{ monthLabel }}</h2>
      <div class="month-controls">
        <el-button :icon="ArrowLeft" aria-label="上个月" @click="emit('move', -1)" />
        <el-button :icon="ArrowRight" aria-label="下个月" @click="emit('move', 1)" />
        <el-button @click="emit('today')">今天</el-button>
      </div>
    </header>
    <div class="calendar-grid">
      <div v-for="day in ['一', '二', '三', '四', '五', '六', '日']" :key="day" class="weekday">{{ day }}</div>
      <div v-for="date in days" :key="date" role="button" tabindex="0" :aria-label="`${date}，${getDayTasks(date).length} 项任务`" :aria-pressed="date === selectedDate" :data-date="date"
        class="day-cell" :class="{ selected: date === selectedDate, adjacent: !date.startsWith(month) }"
        @click="emit('select', date)" @keydown.enter="emit('select', date)" @keydown.space.prevent="emit('select', date)" @dblclick="emit('create', date)">
        <div class="day-heading"><span :class="{ 'today-number': date === today }">{{ Number(date.slice(8)) }}</span><small v-if="date === today">今天</small></div>
        <div class="day-tasks">
          <div v-for="task in getDayTasks(date).slice(0, MAX_VISIBLE_TASKS)" :key="task.id" class="task-preview" :class="{ completed: task.completed }" :style="getDailyTaskMemberStyle(task.memberId)" :title="`${task.memberName} · ${task.title} · ${task.completed ? '已完成' : '待完成'}`">
            <span class="preview-check" aria-hidden="true">{{ task.completed ? '✓' : '' }}</span>
            <span class="preview-member">{{ task.memberName }}</span><span class="preview-separator" aria-hidden="true">·</span><span class="preview-title">{{ task.title }}</span>
          </div>
          <span v-if="getDayTasks(date).length > MAX_VISIBLE_TASKS" class="more-tasks">+ {{ getDayTasks(date).length - MAX_VISIBLE_TASKS }} 项</span>
        </div>
      </div>
    </div>
    <footer>点击日期查看任务 · 双击日期新建任务</footer>
  </section>
</template>

<style scoped>
.task-calendar { min-width:0; padding:20px 12px 14px; }
.calendar-heading { display:flex; align-items:center; gap:16px; padding:0 8px 18px; flex-wrap:wrap; }
h2 { margin:0; font-size:21px; font-weight:650; white-space:nowrap; }
.month-controls { display:flex; gap:8px; }.month-controls :deep(.el-button + .el-button) { margin-left:0; }
.month-controls :deep(.el-button) { min-height:36px; padding:8px 12px; border-radius:9px; }
.calendar-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); border-top:1px solid var(--border); border-left:1px solid var(--border); border-radius:10px; overflow:hidden; }
.weekday { padding:11px 0; text-align:center; font-size:12px; color:var(--text-2); background:var(--surface-weak); border-right:1px solid var(--border); border-bottom:1px solid var(--border); }
.day-cell { min-height:clamp(94px,10.5vh,116px); padding:7px 6px; border-right:1px solid var(--border); border-bottom:1px solid var(--border); cursor:pointer; min-width:0; }
.day-cell:hover { background:#f7faf8; }.day-cell.selected { background:#eef7f1; box-shadow:inset 0 0 0 1px #4b9e7d; border-radius:6px; }
.day-cell:focus-visible { outline:2px solid var(--primary); outline-offset:-3px; }
.day-heading { display:flex; align-items:center; gap:6px; font-size:13px; height:26px; margin-bottom:3px; }.day-heading > span { width:25px; height:25px; display:grid; place-items:center; }
.day-heading small { font-size:11px; color:var(--primary); }.adjacent .day-heading { color:var(--text-3); }
.today-number { color:#fff; background:var(--primary); border-radius:50%; }
.day-tasks { display:grid; gap:4px; }
.task-preview { display:flex; align-items:center; gap:4px; padding:4px 5px 4px 7px; border-radius:5px; background:var(--task-member-background); color:var(--task-member-text); font-size:11px; line-height:1.5; min-width:0; }
.preview-check { flex:none; width:12px; height:12px; border:1.4px solid var(--task-member-accent); border-radius:50%; display:grid; place-items:center; font-size:9px; }
.preview-member { flex:0 1 auto; max-width:40%; font-weight:500; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.preview-separator { flex:none; }
.preview-title { flex:1; min-width:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.completed .preview-title { text-decoration:line-through; text-decoration-thickness:1px; }
.completed .preview-check { background:var(--task-member-accent); color:#fff; }
.more-tasks { font-size:11px; color:var(--text-3); padding-left:4px; }footer { padding:15px 8px 0; color:var(--text-3); font-size:12px; }
@media(max-width:1100px) { .calendar-heading { gap:10px; }h2 { font-size:18px; }.day-cell { min-height:94px; padding:5px 3px; }.task-preview { gap:3px; padding:3px 4px 3px 6px; } }
</style>
