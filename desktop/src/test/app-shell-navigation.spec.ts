import { shallowMount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AppShell from '@/layouts/AppShell.vue'

const testAuthStore = vi.hoisted(() => ({
  user: { role: 'FAMILY_MEMBER', familyId: 12, displayName: '家人' },
  isSuperAdmin: false,
  selectedFamilyId: 12,
  familiesLoaded: true,
  familiesData: [{ id: 12, name: '成家', status: 'ACTIVE' }, { id: 13, name: '李家', status: 'ACTIVE' }],
  selectFamily: vi.fn(),
  logout: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ name: 'photos', meta: { title: '照片管理' } }),
  useRouter: () => ({ replace: vi.fn() }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => testAuthStore,
}))

describe('authenticated compact navigation', () => {
  it('removes global search and switches families from the compact sidebar', async () => {
    testAuthStore.isSuperAdmin = true
    testAuthStore.selectFamily.mockClear()
    const tempWrapper = shallowMount(AppShell, {
      global: { stubs: {
        RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
        RouterView: true,
        ElPopover: { template: '<div><slot name="reference" /><slot /></div>' },
        ElSelect: { template: '<button class="test-select-family" @click="$emit(\'change\', 13)">李家</button>' },
      } },
    })

    expect(tempWrapper.find('.search-box').exists()).toBe(false)
    expect(tempWrapper.find('.topbar').exists()).toBe(false)
    expect(tempWrapper.find('.page-content.photo-page-content').exists()).toBe(true)
    expect(tempWrapper.find('.sidebar .family-switch').exists()).toBe(true)
    expect(tempWrapper.find('.family-switch').attributes('title')).toContain('成家')
    await tempWrapper.find('.test-select-family').trigger('click')
    expect(testAuthStore.selectFamily).toHaveBeenCalledWith(13)
    tempWrapper.unmount()
    testAuthStore.isSuperAdmin = false
  })

  it('keeps icon-only links and account controls accessible by hover labels', () => {
    testAuthStore.isSuperAdmin = false
    const tempWrapper = shallowMount(AppShell, {
      global: {
        stubs: {
          RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
          RouterView: true,
          ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
        },
      },
    })

    expect(tempWrapper.find('.sidebar').classes()).toContain('sidebar-collapsed')
    expect(tempWrapper.findAll('.nav-list a')).toHaveLength(6)
    expect(tempWrapper.find('.nav-list a[href="/photos"]').attributes('title')).toBe('照片管理')
    expect(tempWrapper.find('.nav-list a[href="/photos"]').attributes('aria-label')).toBe('照片管理')
    expect(tempWrapper.find('.member-card').attributes('title')).toBe('家人')
    expect(tempWrapper.find('.logout-button').attributes('aria-label')).toBe('退出登录')
    tempWrapper.unmount()
  })
})
