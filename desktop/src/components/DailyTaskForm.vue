<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { MemberOption } from '@/types/api'
import type { TaskOccurrence, TaskPayload } from '@/types/dailyTask'

const props = defineProps<{ modelValue: boolean; task: TaskOccurrence | null; date: string; currentUserId: number; members: MemberOption[]; submitting: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; submit: [payload: TaskPayload] }>()
type TaskForm = Omit<TaskPayload, 'memberId'> & { memberId: number | undefined }
const formRef = ref<FormInstance>()
const form = reactive<TaskForm>({ title: '', memberId: undefined, taskDate: props.date, taskTime: null, category: '', repeatDaily: false, note: null })
const options = { categories: ['学习', '家庭事务', '运动', '生活', '其他'] }
const rules: FormRules<TaskForm> = {
  title: [{ required: true, whitespace: true, message: '请输入任务标题', trigger: 'blur' }],
  memberId: [{ required: true, message: '请选择成员', trigger: 'change' }],
  taskDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  category: [{ max: 40, message: '分类最多 40 个字符', trigger: 'change' }],
}
watch(() => props.modelValue, (visible) => {
  if (!visible) return
  const tempTask = props.task
  const tempMemberId = props.members.some((member) => member.userId === props.currentUserId) ? props.currentUserId : undefined
  Object.assign(form, tempTask
    ? { title: tempTask.title, memberId: tempTask.memberId, taskDate: tempTask.startDate, taskTime: tempTask.taskTime, category: tempTask.category, repeatDaily: tempTask.repeatDaily, note: tempTask.note }
    : { title: '', memberId: tempMemberId, taskDate: props.date, taskTime: null, category: '', repeatDaily: false, note: null })
})
async function handleSubmit(): Promise<void> {
  const tempValid = await formRef.value?.validate().catch(() => false)
  if (tempValid && !props.submitting && form.memberId !== undefined) emit('submit', { ...form, memberId: form.memberId, title: form.title.trim(), taskTime: form.taskTime || null, note: form.note || null })
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="task ? '编辑任务' : '新建任务'" width="480px" :close-on-click-modal="!submitting" :show-close="!submitting" :close-on-press-escape="!submitting" @update:model-value="emit('update:modelValue', $event)">
    <p v-if="task?.repeatDaily" class="repeat-notice">正在编辑整个重复任务；调整成员、开始日期或重复规则会重置该任务的打卡记录。</p>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="submitting">
      <el-form-item label="任务标题" prop="title"><el-input v-model="form.title" placeholder="例如：阅读 20 分钟" maxlength="120" show-word-limit /></el-form-item>
      <div class="form-grid">
        <el-form-item label="成员" prop="memberId"><el-select v-model="form.memberId" placeholder="请选择成员"><el-option v-for="member in members" :key="member.userId" :label="member.displayName" :value="member.userId" /></el-select></el-form-item>
        <el-form-item label="分类"><el-select v-model="form.category" placeholder="选择或输入分类" allow-create filterable clearable><el-option v-for="category in options.categories" :key="category" :label="category" :value="category" /></el-select></el-form-item>
        <el-form-item :label="form.repeatDaily ? '开始日期' : '任务日期'" prop="taskDate"><el-date-picker v-model="form.taskDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></el-form-item>
        <el-form-item label="时间（选填）"><el-time-picker v-model="form.taskTime" format="HH:mm" value-format="HH:mm:ss" placeholder="不限时间" clearable /></el-form-item>
      </div>
      <el-form-item label="重复"><el-switch v-model="form.repeatDaily" active-text="每天重复" /><span class="repeat-help">{{ form.repeatDaily ? '从开始日期起，每天独立打卡' : '仅在所选日期执行一次' }}</span></el-form-item>
      <el-form-item label="备注（选填）"><el-input v-model="form.note" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button><el-button type="primary" :loading="submitting" @click="handleSubmit">保存任务</el-button></template>
  </el-dialog>
</template>

<style scoped>
.form-grid { display:grid; grid-template-columns:1fr 1fr; gap:0 16px; }.form-grid :deep(.el-select),.form-grid :deep(.el-date-editor) { width:100%; }.repeat-notice { margin:0 0 18px; padding:10px 12px; border-radius:8px; color:#946215; background:#fff6e8; font-size:12px; line-height:1.7; }.repeat-help { display:block; width:100%; font-size:12px; color:var(--text-3); }
</style>
