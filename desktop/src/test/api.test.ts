import { describe, expect, it } from 'vitest'
import { resolveApiBaseUrl } from '@/config/api'

describe('resolveApiBaseUrl', () => {
  it('uses the local Java service in local mode', () => {
    expect(resolveApiBaseUrl('local', undefined)).toBe('http://localhost:8080/api')
  })

  it('uses the configured HTTPS service in server mode', () => {
    expect(resolveApiBaseUrl('server', 'https://family.example.com/api/')).toBe('https://family.example.com/api')
  })

  it('rejects server mode without a configured service address', () => {
    expect(() => resolveApiBaseUrl('server', undefined)).toThrow('VITE_API_BASE_URL')
  })

  it('rejects an insecure server service address', () => {
    expect(() => resolveApiBaseUrl('server', 'http://family.example.com/api')).toThrow('HTTPS')
  })
})
