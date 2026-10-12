<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { familiesApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { Family } from '@/types/api'

interface FamilyForm {
  name: string
  remark: string
}

const familiesData = ref<Family[]>([])
const authStore = useAuthStore()
const isLoading = ref(false)
const isDialogVisible = ref(false)
const isSubmitting = ref(false)
const familyFormRef = ref<FormInstance>()
const familyForm = reactive<FamilyForm>({ name: '', remark: '' })
const familyRules: FormRules<FamilyForm> = {
  name: [{ required: true, message: '请输入家庭名称', trigger: 'blur' }, { max: 100, message: '不能超过 100 字', trigger: 'blur' }],
  remark: [{ max: 500, message: '不能超过 500 字', trigger: 'blur' }],
}

async function loadFamilies(): Promise<void> {
  isLoading.value = true
  try {
    const res = await familiesApi.list()
    familiesData.value = res
    authStore.updateFamilies(res)
  } finally {
    isLoading.value = false
  }
}

function handleCreate(): void {
  Object.assign(familyForm, { name: '', remark: '' })
  isDialogVisible.value = true
}

async function handleSubmit(): Promise<void> {
  const tempValid = await familyFormRef.value?.validate().catch(() => false)
  if (!tempValid || isSubmitting.value) return
  isSubmitting.value = true
  try {
    await familiesApi.create(familyForm)
    ElMessage.success('家庭已创建')
    isDialogVisible.value = false
    await loadFamilies()
  } finally {
    isSubmitting.value = false
  }
}

async function handleStatusChange(family: Family): Promise<void> {
  const tempActive = family.status !== 'ACTIVE'
  const tempAction = tempActive ? '启用' : '停用'
  await ElMessageBox.confirm(`确定${tempAction}“${family.name}”吗？`, `${tempAction}家庭`, { type: 'warning' })
  await familiesApi.changeStatus(family.id, tempActive)
  ElMessage.success(`家庭已${tempAction}`)
  await loadFamilies()
}

onMounted(loadFamilies)
</script>

<template>
  <section class="page-stack">
    <div class="page-toolbar"><el-button class="create-button" type="primary" :icon="Plus" @click="handleCreate">新增家庭</el-button></div>
    <div class="families-grid" v-loading="isLoading">
      <article v-for="family in familiesData" :key="family.id" class="family-card surface">
        <div class="family-avatar">{{ family.name.slice(0, 1) }}</div>
        <div class="family-copy"><div><h3>{{ family.name }}</h3><el-tag :type="family.status === 'ACTIVE' ? 'success' : 'info'" effect="light">{{ family.status === 'ACTIVE' ? '使用中' : '已停用' }}</el-tag></div><p>{{ family.remark }}</p><span>家庭编号 · {{ family.id }}</span></div>
        <el-button :type="family.status === 'ACTIVE' ? 'danger' : 'success'" plain @click="handleStatusChange(family)">{{ family.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
      </article>
      <el-empty v-if="!isLoading && familiesData.length === 0" class="surface" description="暂无家庭" />
    </div>
    <el-dialog v-model="isDialogVisible" title="新增家庭" width="480px" destroy-on-close>
      <el-form ref="familyFormRef" :model="familyForm" :rules="familyRules" label-position="top"><el-form-item label="家庭名称" prop="name"><el-input v-model="familyForm.name" maxlength="100" /></el-form-item><el-form-item label="备注" prop="remark"><el-input v-model="familyForm.remark" type="textarea" :rows="4" maxlength="500" show-word-limit /></el-form-item></el-form>
      <template #footer><el-button @click="isDialogVisible = false">取消</el-button><el-button type="primary" :loading="isSubmitting" @click="handleSubmit">创建家庭</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.create-button { margin-left: auto; }
.families-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 14px; min-height: 200px; }.family-card { display: grid; grid-template-columns: 54px 1fr auto; align-items: center; gap: 16px; padding: 22px; }.family-avatar { display: grid; width: 54px; height: 54px; place-items: center; border-radius: 17px; color: #fff; background: linear-gradient(145deg, #236b4e, #3d8b6b); font-size: 21px; font-weight: 800; }.family-copy { min-width: 0; }.family-copy > div { display: flex; align-items: center; gap: 10px; }.family-copy h3 { margin: 0; }.family-copy p { overflow: hidden; margin: 7px 0; color: var(--muted); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.family-copy span { color: #9aa39e; font-size: 11px; }
</style>
