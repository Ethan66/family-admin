<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Collection,
  DataAnalysis,
  Document,
  Film,
  FolderOpened,
  Grid,
  Lock,
  PictureFilled,
  SwitchButton,
  User,
} from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'

interface PasswordForm {
  currentPassword: string
  newPassword: string
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isPasswordDialogVisible = ref(false)
const isPasswordSubmitting = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordForm = reactive<PasswordForm>({ currentPassword: '', newPassword: '' })
const passwordRules: FormRules<PasswordForm> = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 3, message: '新密码至少 3 位', trigger: 'blur' },
  ],
}

const displayUserName = computed(() => {
  if (!authStore.user) return ''
  // 旧会话可能缓存了初始显示名，超级管理员统一按当前角色名称展示。
  return authStore.isSuperAdmin ? '超级管理员' : authStore.user.displayName
})
const displayRole = computed(() => {
  if (!authStore.user) return ''
  return authStore.user.role === 'SUPER_ADMIN' ? '超级管理员' : '家庭成员'
})

const selectedFamilyName = computed(() => authStore.familiesData.find(
  (family) => family.id === authStore.selectedFamilyId,
)?.name ?? '选择家庭')

async function loadFamilies(): Promise<void> {
  if (!authStore.isSuperAdmin || authStore.familiesLoaded) return
  await authStore.refreshFamilies()
}

function handleFamilyChange(familyId: number): void {
  authStore.selectFamily(familyId)
}

function handleLogout(): void {
  authStore.logout()
  void router.replace('/login')
}

async function handlePasswordSubmit(): Promise<void> {
  const tempValid = await passwordFormRef.value?.validate().catch(() => false)
  if (!tempValid || isPasswordSubmitting.value) return
  isPasswordSubmitting.value = true
  try {
    await authApi.changePassword(passwordForm)
    ElMessage.success('密码已修改，请重新登录')
    isPasswordDialogVisible.value = false
    handleLogout()
  } finally {
    isPasswordSubmitting.value = false
  }
}

onMounted(loadFamilies)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar sidebar-collapsed" aria-label="全局导航">
      <div class="brand" title="家庭管理后台"><span class="brand-mark">家</span><span class="brand-text"><strong>家庭管理后台</strong><small>Family Admin</small></span></div>
      <nav class="nav-list">
        <router-link to="/dashboard" title="家庭概览" aria-label="家庭概览" data-label="家庭概览"><el-icon><grid /></el-icon><span>家庭概览</span></router-link>
        <router-link to="/transactions" title="收入支出" aria-label="收入支出" data-label="收入支出"><el-icon><data-analysis /></el-icon><span>收入支出</span></router-link>
        <router-link to="/exams" title="试卷管理" aria-label="试卷管理" data-label="试卷管理"><el-icon><collection /></el-icon><span>试卷管理</span></router-link>
        <router-link to="/photos" title="照片管理" aria-label="照片管理" data-label="照片管理"><el-icon><picture-filled /></el-icon><span>照片管理</span></router-link>
        <router-link to="/movies" title="影片管理" aria-label="影片管理" data-label="影片管理"><el-icon><film /></el-icon><span>影片管理</span></router-link>
        <router-link to="/notes" title="学习笔记" aria-label="学习笔记" data-label="学习笔记"><el-icon><document /></el-icon><span>学习笔记</span></router-link>
        <template v-if="authStore.isSuperAdmin">
          <div class="nav-separator"></div>
          <router-link to="/families" title="家庭管理" aria-label="家庭管理" data-label="家庭管理"><el-icon><user /></el-icon><span>家庭管理</span></router-link>
          <router-link to="/audit" title="审计日志" aria-label="审计日志" data-label="审计日志"><el-icon><data-analysis /></el-icon><span>审计日志</span></router-link>
        </template>
      </nav>
      <div class="sidebar-foot">
        <el-popover v-if="authStore.isSuperAdmin" placement="right-end" trigger="click" :width="240">
          <template #reference>
            <button class="family-switch" type="button" :title="`切换家庭：${selectedFamilyName}`" :aria-label="`切换家庭：${selectedFamilyName}`"><el-icon><user /></el-icon><span>{{ selectedFamilyName }}</span></button>
          </template>
          <div class="family-popover-title">查看家庭</div>
          <el-select :model-value="authStore.selectedFamilyId" class="family-select" placeholder="选择查看的家庭" filterable @change="handleFamilyChange">
            <el-option v-for="family in authStore.familiesData" :key="family.id" :label="family.name" :value="family.id" :disabled="family.status !== 'ACTIVE'" />
          </el-select>
        </el-popover>
        <el-dropdown class="member-dropdown" trigger="click">
          <button class="member-card" type="button" :title="displayUserName" :aria-label="`${displayUserName}，账户设置`"><span class="avatar">{{ displayUserName.slice(0, 1) }}</span><span class="member-info"><strong>{{ displayUserName }}</strong><small>{{ displayRole }}</small></span></button>
          <template #dropdown><el-dropdown-menu><el-dropdown-item :icon="Lock" @click="isPasswordDialogVisible = true">修改密码</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
        <button class="logout-button" type="button" title="退出登录" aria-label="退出登录" @click="handleLogout"><el-icon><switch-button /></el-icon><span>退出登录</span></button>
        <p class="sidebar-note"><el-icon><folder-opened /></el-icon> 本地资源只保存文件路径，不上传文件</p>
      </div>
    </aside>

    <section class="workspace">
      <main class="page-content" :class="{ 'photo-page-content': route.name === 'photos' }"><router-view /></main>
    </section>

    <el-dialog v-model="isPasswordDialogVisible" title="修改密码" width="440px" destroy-on-close>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
        <el-form-item label="当前密码" prop="currentPassword"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" show-password /></el-form-item>
      </el-form>
      <template #footer><el-button @click="isPasswordDialogVisible = false">取消</el-button><el-button type="primary" :loading="isPasswordSubmitting" @click="handlePasswordSubmit">确认修改</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-shell { --primary:#236b4e; --primary-hover:#185b40; --primary-weak:#eaf4ee; --bg:#f5f6f8; --surface:#fff; --surface-weak:#fafbfc; --surface-sunken:#f2f4f7; --border:#e9ebf0; --border-strong:#dbdfe8; --text:#15181d; --text-2:#5c6472; --text-3:#8a92a0; display:flex; min-height:100vh; color:var(--text); background:var(--bg); font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans SC",sans-serif; font-size:14px; line-height:1.55; }
.sidebar { width:248px; flex:none; position:sticky; top:0; z-index:30; height:100vh; display:flex; flex-direction:column; padding:20px 16px; background:var(--surface); border-right:1px solid var(--border); }
.sidebar-collapsed { width:72px; padding:20px 10px; }
.brand { display:flex; align-items:center; gap:12px; padding:0 8px 24px; }
.sidebar-collapsed .brand { justify-content:center; padding:0 0 24px; }.sidebar-collapsed .brand-text { display:none; }
.brand-mark { width:38px; height:38px; display:grid; place-items:center; border-radius:12px; color:#fff; background:linear-gradient(135deg,#236b4e,#70ad7d); font-size:18px; font-weight:600; box-shadow:0 4px 12px #236b4e47; }
.brand-text,.member-info { display:flex; flex-direction:column; line-height:1.3; }
.brand-text strong { font-size:15px; }.brand-text small { font-size:11px; color:var(--text-3); letter-spacing:.6px; }
.nav-list { display:flex; flex-direction:column; gap:4px; }
.nav-list a { display:flex; align-items:center; gap:12px; height:42px; padding:0 12px; border-radius:12px; color:var(--text-2); text-decoration:none; transition:background .16s,color .16s; }
.sidebar-collapsed .nav-list a { position:relative; justify-content:center; padding:0; }.sidebar-collapsed .nav-list a span { display:none; }
.sidebar-collapsed .nav-list a::after { position:absolute; top:50%; left:calc(100% + 10px); z-index:40; padding:6px 10px; border-radius:6px; background:#25342c; color:#fff; content:attr(data-label); font-size:12px; white-space:nowrap; opacity:0; pointer-events:none; transform:translateY(-50%); visibility:hidden; }
.sidebar-collapsed .nav-list a:hover::after,.sidebar-collapsed .nav-list a:focus-visible::after { opacity:1; visibility:visible; }
.sidebar-collapsed .nav-list a:focus-visible,.sidebar-collapsed .member-card:focus-visible,.sidebar-collapsed .logout-button:focus-visible,.sidebar-collapsed .family-switch:focus-visible { outline:2px solid var(--primary); outline-offset:2px; }
.nav-list a:hover { color:var(--text); background:var(--surface-sunken); }
.nav-list a.router-link-active { color:var(--primary); background:var(--primary-weak); font-weight:600; }
.nav-list .el-icon { font-size:18px; }.nav-separator { margin:8px 0; border-top:1px solid var(--border); }
.sidebar-foot { margin-top:auto; padding-top:24px; }.member-dropdown { display:block; }.member-card { display:flex; align-items:center; gap:12px; width:100%; padding:12px; border:1px solid var(--border); border-radius:12px; background:var(--surface-weak); text-align:left; cursor:pointer; }
.family-switch { display:flex; align-items:center; gap:10px; width:100%; min-height:42px; margin-bottom:9px; padding:8px 12px; border:1px solid var(--border); border-radius:10px; background:var(--surface-weak); color:var(--primary); cursor:pointer; }.family-switch .el-icon { font-size:18px; }.family-popover-title { margin-bottom:9px; color:var(--text-2); font-size:12px; }.family-select { width:100%; }
.sidebar-collapsed .family-switch { justify-content:center; padding:8px; }.sidebar-collapsed .family-switch span { display:none; }
.sidebar-collapsed .sidebar-foot { padding-top:12px; }.sidebar-collapsed .member-card { justify-content:center; height:48px; padding:6px; }.sidebar-collapsed .member-info { display:none; }
.avatar { width:34px; height:34px; display:grid; place-items:center; border-radius:50%; color:#236b4e; background:#e5f2eb; font-size:13px; font-weight:600; }
.member-info strong { font-size:13px; }.member-info small { font-size:11px; color:var(--text-3); }
.logout-button { width:100%; height:32px; margin-top:12px; border:1px solid var(--border-strong); border-radius:8px; color:var(--text-2); background:#fff; cursor:pointer; }
.sidebar-collapsed .logout-button { display:grid; height:42px; place-items:center; margin-top:8px; font-size:18px; }.sidebar-collapsed .logout-button span { display:none; }.sidebar-collapsed .sidebar-note { display:none; }
.sidebar-note { margin:16px 8px 0; color:var(--text-3); font-size:11px; line-height:1.6; }.sidebar-note .el-icon { vertical-align:middle; }
.workspace { flex:1; min-width:0; }
.page-content { padding:24px 32px 40px; }
.page-content.photo-page-content { padding:0; }
@media (max-width:1200px) { .page-content { padding:20px; } }
</style>
