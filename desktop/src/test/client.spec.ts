import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, setAccessToken, setAuthFailureHandler } from '@/api/client'

const { showError } = vi.hoisted(() => ({ showError: vi.fn() }))

vi.mock('element-plus', () => ({ ElMessage: { error: showError } }))

describe('API authentication failures', () => {
  beforeEach(() => {
    showError.mockClear()
    setAccessToken('active-token')
  })

  it('allows requests to wait up to 60 seconds', () => {
    expect(http.defaults.timeout).toBe(60_000)
  })

  it.each([401, 403])('invalidates the session on HTTP %i', async (status) => {
    const tempHandleFailure = vi.fn(() => setAccessToken(''))
    setAuthFailureHandler(tempHandleFailure)

    await expect(http.get('/transactions', {
      adapter: async (config) => {
        throw new AxiosError('denied', 'ERR_BAD_RESPONSE', config, undefined, {
          data: { code: 'AUTH_ERROR', message: 'denied', data: null },
          status,
          statusText: 'Denied',
          headers: {},
          config,
        })
      },
    })).rejects.toBeInstanceOf(AxiosError)

    expect(tempHandleFailure).toHaveBeenCalledOnce()
    expect(showError).not.toHaveBeenCalled()
  })

  it('keeps other request errors in the normal error flow', async () => {
    const tempHandleFailure = vi.fn()
    setAuthFailureHandler(tempHandleFailure)

    await expect(http.get('/transactions', {
      adapter: async (config) => {
        throw new AxiosError('server error', 'ERR_BAD_RESPONSE', config, undefined, {
          data: { code: 'SYSTEM_ERROR', message: '服务异常', data: null },
          status: 500,
          statusText: 'Server Error',
          headers: {},
          config,
        })
      },
    })).rejects.toBeInstanceOf(AxiosError)

    expect(tempHandleFailure).not.toHaveBeenCalled()
    expect(showError).toHaveBeenCalledWith('服务异常')
  })
})
