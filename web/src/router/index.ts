import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import AppShell from '@/layout/AppShell.vue'
import type { CurrentUser } from '@/types'

export const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
  { path: '/', component: AppShell, redirect: '/dashboard', children: [
    { path: 'dashboard', component: () => import('@/views/DashboardView.vue') },
    { path: 'transactions', component: () => import('@/views/TransactionsView.vue') },
    { path: 'exams', component: () => import('@/views/ExamsView.vue') },
    { path: 'photos', component: () => import('@/views/PhotosView.vue') },
    { path: 'movies', component: () => import('@/views/MoviesView.vue') },
    { path: 'notes', component: () => import('@/views/NotesView.vue') },
    { path: 'families', component: () => import('@/views/FamiliesView.vue'), meta: { superAdmin: true } },
    { path: 'audit', component: () => import('@/views/AuditView.vue'), meta: { superAdmin: true } },
  ]},
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach((to) => {
  const token = localStorage.getItem('family-admin-token')
  if (!to.meta.public && !token) return '/login'
  if (to.path === '/login' && token) return '/dashboard'
  if (to.meta.superAdmin) {
    const user = JSON.parse(localStorage.getItem('family-admin-user') || 'null') as CurrentUser | null
    if (user?.role !== 'SUPER_ADMIN') return '/dashboard'
  }
})
export default router
