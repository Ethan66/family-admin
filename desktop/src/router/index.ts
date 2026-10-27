import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AppShell.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '家庭概览' } },
      { path: 'transactions', name: 'transactions', component: () => import('@/views/TransactionsView.vue'), meta: { title: '收入支出' } },
      { path: 'passbooks', name: 'passbooks', component: () => import('@/views/PassbooksView.vue'), meta: { title: '存折' } },
      { path: 'daily-tasks', name: 'daily-tasks', component: () => import('@/views/DailyTasksView.vue'), meta: { title: '每日任务' } },
      { path: 'courses', name: 'courses', component: () => import('@/views/CoursesView.vue'), meta: { title: '课程表' } },
      { path: 'photos', name: 'photos', component: () => import('@/views/PhotosView.vue'), meta: { title: '照片管理' } },
      { path: 'movies', name: 'movies', component: () => import('@/views/MoviesView.vue'), meta: { title: '影片管理' } },
      { path: 'notes', name: 'notes', component: () => import('@/views/NotesView.vue'), meta: { title: '学习笔记' } },
      { path: 'quick-notes', name: 'quick-notes', component: () => import('@/views/QuickNotesView.vue'), meta: { title: '随身记' } },
      { path: 'exams', name: 'exams', component: () => import('@/views/ExamsView.vue'), meta: { title: '试卷管理' } },
      { path: 'families', name: 'families', component: () => import('@/views/FamiliesView.vue'), meta: { title: '家庭管理', superAdmin: true } },
      { path: 'audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { title: '审计日志', superAdmin: true } },
    ],
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach((to) => {
  const authStore = useAuthStore(pinia)
  if (!to.meta.public && !authStore.token) return '/login'
  if (to.path === '/login' && authStore.token) return '/dashboard'
  if (to.meta.superAdmin && !authStore.isSuperAdmin) return '/dashboard'
})

export default router
