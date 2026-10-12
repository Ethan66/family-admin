import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from '@/App.vue'

describe('App', () => {
  it('renders the application root', () => {
    const wrapper = mount(App, {
      global: { stubs: { RouterView: true } },
    })

    expect(wrapper.find('[data-testid="app-root"]').exists()).toBe(true)
  })
})
