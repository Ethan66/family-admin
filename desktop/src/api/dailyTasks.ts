import { apiDelete, apiGet, apiPost, apiPut } from '@/api/client'
import type { TaskOccurrence, TaskPayload } from '@/types/dailyTask'

export const dailyTasksApi = {
  apiList: (params: { from: string; to: string; memberId?: number }) => apiGet<TaskOccurrence[]>('/daily-tasks', { params }),
  apiCreate: (body: TaskPayload) => apiPost<void>('/daily-tasks', body),
  apiUpdate: (id: number, body: TaskPayload) => apiPut<void>(`/daily-tasks/${id}`, body),
  apiRemove: (id: number) => apiDelete(`/daily-tasks/${id}`),
  apiComplete: (id: number, date: string, completed: boolean) => apiPut<void>(`/daily-tasks/${id}/completion`, { date, completed }),
}
