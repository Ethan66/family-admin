import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, familiesApi } from '@/api/modules'
import { setAccessToken, setSelectedFamilyId as applySelectedFamilyId } from '@/api/client'
import type { CurrentUser, Family } from '@/types/api'

const AUTH_SESSION_KEY = 'family-admin-auth-session'

interface AuthSession {
  token: string
  user: CurrentUser
  selectedFamilyId?: number | null
  families?: Family[]
}

/** 恢复当前应用窗口内的登录状态，损坏的数据会被清理并按未登录处理。 */
function readAuthSession(): AuthSession | null {
  const tempSessionJson = sessionStorage.getItem(AUTH_SESSION_KEY)
  if (!tempSessionJson) return null
  try {
    return JSON.parse(tempSessionJson) as AuthSession
  } catch {
    sessionStorage.removeItem(AUTH_SESSION_KEY)
    return null
  }
}

export const useAuthStore = defineStore('auth', () => {
  const tempAuthSession = readAuthSession()
  const token = ref(tempAuthSession?.token ?? '')
  const user = ref<CurrentUser | null>(tempAuthSession?.user ?? null)
  const selectedFamilyId = ref<number | null>(tempAuthSession?.selectedFamilyId ?? null)
  const familiesData = ref<Family[]>(tempAuthSession?.families ?? [])
  const familiesLoaded = ref(tempAuthSession?.families !== undefined)
  const isSuperAdmin = computed(() => user.value?.role === 'SUPER_ADMIN')
  setAccessToken(token.value)
  applySelectedFamilyId(selectedFamilyId.value)

  function persistSession(): void {
    if (!token.value || !user.value) return
    sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify({
      token: token.value,
      user: user.value,
      selectedFamilyId: selectedFamilyId.value,
      families: familiesData.value,
    }))
  }

  async function login(username: string, password: string): Promise<void> {
    const res = await authApi.login({ username, password })
    token.value = res.accessToken
    user.value = res.user
    setAccessToken(res.accessToken)
    selectedFamilyId.value = null
    applySelectedFamilyId(null)
    familiesData.value = []
    familiesLoaded.value = false
    try {
      if (res.user.role === 'SUPER_ADMIN') await refreshFamilies()
      else persistSession()
    } catch (error) {
      logout()
      throw error
    }
  }

  function selectFamily(familyId: number | null): void {
    selectedFamilyId.value = familyId
    applySelectedFamilyId(familyId)
    persistSession()
  }

  function updateFamilies(families: Family[]): void {
    familiesData.value = families
    familiesLoaded.value = true
    const tempActiveFamilies = families.filter((family) => family.status === 'ACTIVE')
    const tempCurrentFamily = tempActiveFamilies.find((family) => family.id === selectedFamilyId.value)
    const tempDefaultFamily = tempActiveFamilies.find((family) => family.name === '成家') ?? tempActiveFamilies[0]
    selectFamily(tempCurrentFamily?.id ?? tempDefaultFamily?.id ?? null)
  }

  async function refreshFamilies(): Promise<void> {
    updateFamilies(await familiesApi.list())
  }

  function logout(): void {
    token.value = ''
    user.value = null
    selectedFamilyId.value = null
    familiesData.value = []
    familiesLoaded.value = false
    setAccessToken('')
    applySelectedFamilyId(null)
    sessionStorage.removeItem(AUTH_SESSION_KEY)
  }

  return { token, user, selectedFamilyId, familiesData, familiesLoaded, isSuperAdmin, login, selectFamily, updateFamilies, refreshFamilies, logout }
})
