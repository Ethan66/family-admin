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

  it('restores login after closing the window and keeps renewed tokens persistently', async () => {
    apiLogin.mockResolvedValue({
      accessToken: 'session-token',
      user: { id: 1, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' },
    })
    const authStore = useAuthStore()

    await authStore.login('member', 'password')

    expect(authStore.token).toBe('session-token')
    expect(authStore.user?.familyId).toBe(12)
    expect(localStorage.length).toBe(1)
    authStore.renewSession('renewed-token')
    sessionStorage.clear()

    setActivePinia(createPinia())
    const restoredAuthStore = useAuthStore()
    expect(restoredAuthStore.token).toBe('renewed-token')
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
    expect(localStorage.length).toBe(0)
  })

  it('migrates the earlier window session and clears both stores on logout', () => {
    sessionStorage.setItem('family-admin-auth-session', JSON.stringify({
      token: 'earlier-token',
      user: { id: 1, familyId: 12, username: 'member', displayName: '家庭成员', role: 'FAMILY_MEMBER' },
    }))
    const tempAuthStore = useAuthStore()
    expect(tempAuthStore.token).toBe('earlier-token')
    expect(sessionStorage.length).toBe(0)
    expect(localStorage.length).toBe(1)
    tempAuthStore.logout()
    expect(localStorage.length).toBe(0)
  })

  it('does not restore a logged out session from a late renewal', () => {
    const tempAuthStore = useAuthStore()
    tempAuthStore.logout()
    tempAuthStore.renewSession('late-token')
    expect(tempAuthStore.token).toBe('')
    expect(localStorage.length).toBe(0)
  })
})
