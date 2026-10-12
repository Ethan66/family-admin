import { http } from './client'
import type { CurrentUser, DashboardOverview, Exam, Family, Movie, PageResult, Photo, StudyNote, Transaction } from '@/types'

const page = <T>(url: string, params?: object) => http.get<never, PageResult<T>>(url, { params })
const remove = (url: string) => http.delete(url)

export const authApi = {
  login: (body: { username: string; password: string }) => http.post<never, { accessToken: string; user: CurrentUser }>('/auth/login', body),
  me: () => http.get<never, CurrentUser>('/auth/me'),
  changePassword: (body: { currentPassword: string; newPassword: string }) => http.put('/auth/password', body),
}
export const dashboardApi = { overview: () => http.get<never, DashboardOverview>('/dashboard') }
export const transactionsApi = {
  list: (params?: object) => page<Transaction>('/transactions', params),
  summary: (params?: object) => http.get<never, { income: number; expense: number; balance: number }>('/transactions/summary', { params }),
  create: (body: object) => http.post('/transactions', body), update: (id: number, body: object) => http.put(`/transactions/${id}`, body), remove: (id: number) => remove(`/transactions/${id}`),
}
export const examsApi = {
  list: (params?: object) => page<Exam>('/exams', params), create: (body: object) => http.post('/exams', body), update: (id: number, body: object) => http.put(`/exams/${id}`, body),
  review: (id: number, body: object) => http.put(`/exams/${id}/review`, body), remove: (id: number) => remove(`/exams/${id}`),
}
export const photosApi = {
  list: (params?: object) => page<Photo>('/photos', params), create: (body: object) => http.post('/photos', body), update: (id: number, body: object) => http.put(`/photos/${id}`, body), remove: (id: number) => remove(`/photos/${id}`),
  upload: (file: File) => { const body = new FormData(); body.append('file', file); return http.post<never, { id: number }>('/files', body) },
}
export const filesApi = { content: (id: number) => http.get<never, Blob>(`/files/${id}`, { responseType: 'blob' }) }
export const moviesApi = { list: (params?: object) => page<Movie>('/movies', params), create: (body: object) => http.post('/movies', body), update: (id: number, body: object) => http.put(`/movies/${id}`, body), remove: (id: number) => remove(`/movies/${id}`) }
export const notesApi = { list: (params?: object) => page<StudyNote>('/notes', params), remove: (id: number) => remove(`/notes/${id}`) }
export interface FamilyMember { userId: number; familyId: number; username: string; displayName: string; role: string; status: string }
export const familiesApi = {
  list: () => http.get<never, Family[]>('/admin/families'), create: (body: object) => http.post('/admin/families', body),
  changeStatus: (id: number, status: string) => http.put(`/admin/families/${id}/status`, { status }),
  members: (id: number) => http.get<never, FamilyMember[]>(`/admin/families/${id}/members`),
  createMember: (id: number, body: object) => http.post(`/admin/families/${id}/members`, body),
  updateMember: (familyId: number, userId: number, body: object) => http.put(`/admin/families/${familyId}/members/${userId}`, body),
}
export interface AuditLog { id: number; familyId?: number; operatorId: number; action: string; resourceType: string; resourceId?: string; result: string; createdAt: string }
export const auditApi = { list: (params?: object) => page<AuditLog>('/admin/audit-logs', params) }
