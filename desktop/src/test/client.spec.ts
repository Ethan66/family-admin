import { AxiosError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { http, setAccessToken, setAuthFailureHandler, setTokenRenewalHandler } from '@/api/client'

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

  it('uses a renewed token for subsequent requests and notifies persistence', async () => {
    const tempRenew = vi.fn()
    setTokenRenewalHandler(tempRenew)
    await http.get('/auth/me', { adapter: async (config) => ({ data: {}, status: 200, statusText: 'OK', headers: { 'x-access-token': 'renewed-token' }, config }) })
    expect(tempRenew).toHaveBeenCalledWith('renewed-token')
    await http.get('/auth/me', { adapter: async (config) => {
      expect(config.headers.Authorization).toBe('Bearer renewed-token')
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    } })
  })

  it('ignores renewal and auth errors from an earlier login session', async () => {
    const tempRenew = vi.fn()
    const tempFailure = vi.fn()
    setTokenRenewalHandler(tempRenew)
    setAuthFailureHandler(tempFailure)
    await http.get('/auth/me', { adapter: async (config) => {
      setAccessToken('new-login-token')
      return { data: {}, status: 200, statusText: 'OK', headers: { 'x-access-token': 'old-renewal' }, config }
    } })
    expect(tempRenew).not.toHaveBeenCalled()
    await expect(http.get('/auth/me', { adapter: async (config) => {
      setAccessToken('')
      throw new AxiosError('denied', 'ERR_BAD_RESPONSE', config, undefined, { data: {}, status: 401, statusText: 'Denied', headers: {}, config })
    } })).rejects.toBeInstanceOf(AxiosError)
    expect(tempFailure).not.toHaveBeenCalled()
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
