import axios from 'axios'
import { ElMessage } from 'element-plus'

interface ApiEnvelope<T> { code: string; message: string; data: T }

export const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('family-admin-token')
  const familyId = localStorage.getItem('family-admin-selected-family')
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (familyId) config.headers['X-Family-Id'] = familyId
  return config
})

http.interceptors.response.use(
  (response) => response.data instanceof Blob
    ? response.data as never
    : (response.data as ApiEnvelope<unknown>).data as never,
  (error) => {
    const message = error.response?.data?.message || '请求失败，请稍后重试'
    if (error.response?.status === 401) {
      localStorage.removeItem('family-admin-token')
      if (location.pathname !== '/login') location.href = '/login'
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)
