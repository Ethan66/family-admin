import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/modules'
import type { CurrentUser } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('family-admin-token') || '')
  const user = ref<CurrentUser | null>(JSON.parse(localStorage.getItem('family-admin-user') || 'null'))
  const isSuperAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')
  async function login(username: string, password: string) {
    const result = await authApi.login({ username, password })
    token.value = result.accessToken; user.value = result.user
    localStorage.setItem('family-admin-token', result.accessToken)
    localStorage.setItem('family-admin-user', JSON.stringify(result.user))
  }
  function logout() {
    token.value = ''; user.value = null
    localStorage.removeItem('family-admin-token'); localStorage.removeItem('family-admin-user'); localStorage.removeItem('family-admin-selected-family')
  }
  return { token, user, isSuperAdmin, login, logout }
})
