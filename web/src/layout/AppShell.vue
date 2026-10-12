<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Collection, DataAnalysis, DocumentChecked, Film, Lock, Memo, Money, Picture, Setting, SwitchButton } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { authApi, familiesApi } from '@/api/modules'
import { useAuthStore } from '@/stores/auth'
import type { Family } from '@/types'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const families = ref<Family[]>([])
const selectedFamily = ref(localStorage.getItem('family-admin-selected-family') || '')
const passwordVisible = ref(false)
const passwordSaving = ref(false)
const passwordForm = reactive({ currentPassword: '', newPassword: '' })
const menus = computed(() => [
  { path: '/dashboard', label: '家庭概览', icon: DataAnalysis },
  { path: '/transactions', label: '收入支出', icon: Money },
  { path: '/exams', label: '试卷管理', icon: Collection },
  { path: '/photos', label: '照片管理', icon: Picture },
  { path: '/movies', label: '影片管理', icon: Film },
  { path: '/notes', label: '学习笔记', icon: Memo },
  ...(auth.isSuperAdmin ? [{ path: '/families', label: '家庭管理', icon: Setting }] : []),
  ...(auth.isSuperAdmin ? [{ path: '/audit', label: '审计日志', icon: DocumentChecked }] : []),
])

onMounted(async () => {
  if (auth.isSuperAdmin) {
    families.value = await familiesApi.list()
    if (!selectedFamily.value && families.value.length) selectFamily(String(families.value[0].id))
  }
})
function selectFamily(value: string) {
  selectedFamily.value = value
  if (value) localStorage.setItem('family-admin-selected-family', value)
  else localStorage.removeItem('family-admin-selected-family')
  if (route.path !== '/families') router.go(0)
}
function logout() { auth.logout(); router.replace('/login') }
async function changePassword() {
  passwordSaving.value = true
  try {
    await authApi.changePassword(passwordForm)
    ElMessage.success('密码已修改，请重新登录')
    passwordVisible.value = false
    logout()
  } finally { passwordSaving.value = false }
}
</script>

<template>
  <div class="shell">
    <aside class="sidebar" :class="{ collapsed }">
      <button class="brand" type="button" @click="collapsed = !collapsed" aria-label="收起导航">
        <span class="brand-mark">家</span><span v-if="!collapsed" class="brand-copy"><strong>家序</strong><small>家庭资料台</small></span>
      </button>
      <nav aria-label="主要导航">
        <router-link v-for="item in menus" :key="item.path" :to="item.path" class="nav-item" :title="item.label">
          <el-icon><component :is="item.icon" /></el-icon><span v-if="!collapsed">{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="account">
        <div v-if="!collapsed" class="account-copy"><strong>{{ auth.user?.displayName || '家庭成员' }}</strong><small>{{ auth.user?.role === 'SUPER_ADMIN' ? '超级管理员' : '家庭成员' }}</small></div>
        <div class="account-actions"><el-button circle text aria-label="修改密码" @click="passwordVisible=true"><el-icon><Lock /></el-icon></el-button><el-button circle text aria-label="退出登录" @click="logout"><el-icon><SwitchButton /></el-icon></el-button></div>
      </div>
    </aside>
    <section class="main">
      <header class="topbar">
        <div><span class="crumb">家庭资料</span><span class="slash">/</span><strong>{{ menus.find(item => item.path === route.path)?.label || '工作台' }}</strong></div>
        <el-select v-if="auth.isSuperAdmin" :model-value="selectedFamily" placeholder="选择要查看的家庭" class="family-select" @change="selectFamily">
          <el-option v-for="family in families" :key="family.id" :label="family.name" :value="String(family.id)" />
        </el-select>
      </header>
      <main class="content"><router-view v-slot="{ Component }"><transition name="page" mode="out-in"><component :is="Component" :key="route.path" /></transition></router-view></main>
    </section>
    <el-dialog v-model="passwordVisible" title="修改密码" width="min(440px,92vw)"><el-form label-position="top"><el-form-item label="当前密码"><el-input v-model="passwordForm.currentPassword" type="password" show-password /></el-form-item><el-form-item label="新密码"><el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="至少 8 位" /></el-form-item></el-form><template #footer><el-button @click="passwordVisible=false">取消</el-button><el-button type="primary" :loading="passwordSaving" @click="changePassword">确认修改</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.shell { min-height: 100dvh; display: flex; background: var(--canvas); }
.sidebar { position: sticky; top: 0; width: 236px; height: 100dvh; flex: 0 0 auto; display: flex; flex-direction: column; padding: 22px 14px; color: #e9efe9; background: var(--forest); transition: width .22s ease; }
.sidebar.collapsed { width: 76px; }
.brand { display: flex; align-items: center; gap: 12px; height: 52px; padding: 0 8px; color: inherit; background: transparent; border: 0; cursor: pointer; text-align: left; }
.brand-mark { width: 36px; height: 36px; flex: 0 0 auto; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.26); border-radius: 11px; font-family: Georgia, serif; font-size: 20px; background: rgba(255,255,255,.1); }
.brand-copy { display: grid; white-space: nowrap; }.brand-copy strong { font: 600 20px Georgia, "Songti SC", serif; }.brand-copy small { margin-top: 2px; color: #aabdb5; font-size: 11px; letter-spacing: .12em; }
nav { display: grid; gap: 5px; margin-top: 34px; }
.nav-item { position: relative; height: 44px; display: flex; align-items: center; gap: 13px; padding: 0 14px; border-radius: 10px; color: #aec0b8; font-size: 14px; transition: color .18s, background .18s; }
.nav-item:hover { color: #fff; background: rgba(255,255,255,.06); }.nav-item.router-link-active { color: #fff; background: rgba(255,255,255,.12); }.nav-item.router-link-active::before { content: ''; position: absolute; left: -14px; width: 3px; height: 22px; border-radius: 0 3px 3px 0; background: #e7ad72; }
.account { margin-top: auto; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 14px 8px 0; border-top: 1px solid rgba(255,255,255,.12); }.account-copy { min-width: 0; display: grid; }.account-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }.account-copy small { color: #9eb2a9; font-size: 11px; margin-top: 3px; }
.account-actions { display: flex; }
.main { min-width: 0; flex: 1; }.topbar { height: 72px; display: flex; align-items: center; justify-content: space-between; padding: 0 clamp(20px, 4vw, 54px); border-bottom: 1px solid var(--line); background: rgba(243,240,232,.9); backdrop-filter: blur(12px); }.crumb,.slash { color: var(--muted); }.slash { margin: 0 10px; }.family-select { width: 220px; }.content { width: min(1440px, 100%); margin: 0 auto; padding: 36px clamp(18px, 4vw, 54px) 64px; }
@media (max-width: 720px) { .sidebar { width: 68px; padding-inline: 10px; }.sidebar:not(.collapsed) { width: 68px; }.brand-copy,.nav-item span,.account-copy { display: none; }.nav-item { justify-content: center; padding: 0; }.nav-item.router-link-active::before { left: -10px; }.topbar { padding: 0 18px; }.crumb,.slash { display: none; }.family-select { width: 160px; }.content { padding-top: 24px; } }
</style>
