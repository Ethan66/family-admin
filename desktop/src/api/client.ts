import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { getApiBaseUrl } from '@/config/api'
import type { ApiEnvelope } from '@/types/api'

let accessToken = ''
let selectedFamilyId: number | null = null
let authFailureHandler: (() => void) | null = null
let tokenRenewalHandler: ((token: string) => void) | null = null

export const http = axios.create({
  baseURL: getApiBaseUrl(),
  timeout: 60_000,
})

export function setAccessToken(token: string): void {
  accessToken = token
}

export function setSelectedFamilyId(familyId: number | null): void {
  selectedFamilyId = familyId
}

/** 由应用入口注册登录失效后的会话清理和路由跳转。 */
export function setAuthFailureHandler(handler: () => void): void {
  authFailureHandler = handler
}

/** 将已确认属于当前会话的续期令牌交给登录状态保存。 */
export function setTokenRenewalHandler(handler: (token: string) => void): void {
  tokenRenewalHandler = handler
}

http.interceptors.request.use((config) => {
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  if (selectedFamilyId !== null) config.headers['X-Family-Id'] = String(selectedFamilyId)
  return config
})

http.interceptors.response.use(
  (response) => {
    const tempToken = response.headers['x-access-token']
    // 只接受当前令牌发出的请求，避免退出、切换账号或较新续期被旧响应覆盖。
    if (typeof tempToken === 'string' && tempToken && accessToken
      && response.config.headers.Authorization === `Bearer ${accessToken}`) {
      accessToken = tempToken
      tokenRenewalHandler?.(tempToken)
    }
    return response
  },
  (error: unknown) => {
    if (axios.isAxiosError<ApiEnvelope<unknown>>(error)) {
      const tempStatus = error.response?.status
      if ((tempStatus === 401 || tempStatus === 403) && error.config?.headers?.Authorization) {
        if (accessToken && error.config.headers.Authorization === `Bearer ${accessToken}`) authFailureHandler?.()
        return Promise.reject(error)
      }
      ElMessage.error(error.response?.data.message ?? '请求失败，请稍后重试')
    } else {
      ElMessage.error('请求失败，请稍后重试')
    }
    return Promise.reject(error)
  },
)

export async function apiGet<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const res = await http.get<ApiEnvelope<T>>(url, config)
  return res.data.data
}

export async function apiPost<T>(url: string, body: unknown): Promise<T> {
  const res = await http.post<ApiEnvelope<T>>(url, body)
  return res.data.data
}

export async function apiPut<T>(url: string, body: unknown): Promise<T> {
  const res = await http.put<ApiEnvelope<T>>(url, body)
  return res.data.data
}

export async function apiDelete(url: string): Promise<void> {
  await http.delete<ApiEnvelope<null>>(url)
}
