import { shallowMount } from '@vue/test-utils'
import { ElSelect } from 'element-plus'
import { describe, expect, it } from 'vitest'
import DailyTaskForm from '@/components/DailyTaskForm.vue'

const members = [{ userId: 7, displayName: '爸爸' }, { userId: 8, displayName: '妈妈' }]

describe('daily task family member choices', () => {
  it('lists family members and defaults to the signed-in member when present', async () => {
    const tempWrapper = shallowMount(DailyTaskForm, {
      props: { modelValue: false, task: null, date: '2026-10-24', currentUserId: 7, members, submitting: false },
      global: { renderStubDefaultSlot: true },
    })
    await tempWrapper.setProps({ modelValue: true })
    const tempSelect = tempWrapper.findComponent(ElSelect)
    expect(tempSelect.props('modelValue')).toBe(7)
    expect(tempSelect.findAllComponents({ name: 'ElOption' }).map((option) => option.props('value'))).toEqual([7, 8])
    tempWrapper.unmount()
  })
  it('does not default to an administrator outside the family', async () => {
    const tempWrapper = shallowMount(DailyTaskForm, {
      props: { modelValue: false, task: null, date: '2026-10-24', currentUserId: 1, members, submitting: false },
      global: { renderStubDefaultSlot: true },
    })
    await tempWrapper.setProps({ modelValue: true })
    expect(tempWrapper.findComponent(ElSelect).props('modelValue')).toBeUndefined()
    tempWrapper.unmount()
  })
})
