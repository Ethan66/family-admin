import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'

const { apiLogin, apiFamilies } = vi.hoisted(() => ({ apiLogin: vi.fn(), apiFamilies: vi.fn() }))

vi.mock('@/api/modules', () => ({ authApi: { login: apiLogin }, familiesApi: { list: apiFamilies } }))

describe('auth store', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
    setActivePinia(createPinia())
    apiLogin.mockReset()
    apiFamilies.mockReset()
  })

  it('restores the access token after a page reload in the active application session', async () => {
    apiLogin.mockResolvedValue({
      accessToken: 'session-token',
      user: { id: 1, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' },
    })
    const authStore = useAuthStore()

    await authStore.login('member', 'password')

    expect(authStore.token).toBe('session-token')
    expect(authStore.user?.familyId).toBe(12)
    expect(sessionStorage.length).toBe(1)
    expect(localStorage.length).toBe(0)

    setActivePinia(createPinia())
    const restoredAuthStore = useAuthStore()
    expect(restoredAuthStore.token).toBe('session-token')
    expect(restoredAuthStore.user?.familyId).toBe(12)
    expect(apiFamilies).not.toHaveBeenCalled()
  })

  it('selects 成家 before a super administrator enters the dashboard and restores it from the session', async () => {
    apiLogin.mockResolvedValue({
      accessToken: 'admin-token',
      user: { id: 1, familyId: null, username: 'admin', displayName: '超级管理员', role: 'SUPER_ADMIN' },
    })
    apiFamilies.mockResolvedValue([
      { id: 2, name: '李家', status: 'ACTIVE', remark: null },
      { id: 1, name: '成家', status: 'ACTIVE', remark: null },
    ])
    const authStore = useAuthStore()

    await authStore.login('admin', 'password')

    expect(authStore.selectedFamilyId).toBe(1)
    expect(authStore.familiesData).toHaveLength(2)
    expect(apiFamilies).toHaveBeenCalledOnce()

    setActivePinia(createPinia())
    const restoredAuthStore = useAuthStore()
    expect(restoredAuthStore.selectedFamilyId).toBe(1)
    expect(restoredAuthStore.familiesData).toHaveLength(2)
    expect(restoredAuthStore.familiesLoaded).toBe(true)
    expect(apiFamilies).toHaveBeenCalledOnce()
  })

  it('remembers a manually selected family and falls back to 成家 if that family is disabled', async () => {
    apiLogin.mockResolvedValue({
      accessToken: 'admin-token',
      user: { id: 1, familyId: null, username: 'admin', displayName: '超级管理员', role: 'SUPER_ADMIN' },
    })
    apiFamilies.mockResolvedValue([
      { id: 2, name: '李家', status: 'ACTIVE', remark: null },
      { id: 1, name: '成家', status: 'ACTIVE', remark: null },
    ])
    const authStore = useAuthStore()
    await authStore.login('admin', 'password')
    authStore.selectFamily(2)

    setActivePinia(createPinia())
    const restoredAuthStore = useAuthStore()
    expect(restoredAuthStore.selectedFamilyId).toBe(2)

    restoredAuthStore.updateFamilies([
      { id: 2, name: '李家', status: 'DISABLED', remark: null },
      { id: 1, name: '成家', status: 'ACTIVE', remark: null },
    ])
    expect(restoredAuthStore.selectedFamilyId).toBe(1)
  })

  it('clears the selected family when logging out', () => {
    const authStore = useAuthStore()
    authStore.selectedFamilyId = 21

    authStore.logout()

    expect(authStore.selectedFamilyId).toBeNull()
    expect(authStore.familiesData).toEqual([])
    expect(authStore.user).toBeNull()
    expect(sessionStorage.length).toBe(0)
  })
})
