import { apiGet, apiPost, apiPut, apiDelete } from '@/api/client'
import type { CourseBalance, CourseOccurrence, CoursePayload, CourseRecord, CourseStatus } from '@/types/course'

export const coursesApi = {
  apiList: (params: { memberId?: number }) => apiGet<CourseOccurrence[]>('/courses', { params }),
  apiHistory: (params: { page: number; memberId?: number; title?: string; status?: Exclude<CourseStatus, 'PENDING'>; month?: string }) => apiGet<{ records: CourseRecord[]; total: number }>('/courses/records', { params }),
  apiBalances: (params: { memberId?: number }) => apiGet<CourseBalance[]>('/courses/balances', { params }),
  apiSaveBalance: (body: { memberId: number; title: string; remainingLessons: number; totalLessons: number | null }) => apiPut<void>('/courses/balances', body),
  apiCreate: (body: CoursePayload) => apiPost<void>('/courses', body),
  apiUpdate: (id: number, body: CoursePayload) => apiPut<void>(`/courses/${id}`, body),
  apiRemove: (id: number) => apiDelete(`/courses/${id}`),
  apiRecord: (id: number, body: { date: string; status: Exclude<CourseStatus, 'PENDING'>; note: string }) => apiPut<void>(`/courses/${id}/attendance`, body),
}
