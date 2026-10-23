import { apiDelete, apiGet, apiPost, apiPut } from './client'
import type {
  AuditLog,
  CurrentUser,
  DashboardOverview,
  Family,
  LoginResponse,
  MemberOption,
  PageResult,
  TransactionPayload,
  TransactionRecord,
  TransactionSummary,
} from '@/types/api'

export const authApi = {
  login: (body: { username: string; password: string }) => apiPost<LoginResponse>('/auth/login', body),
  me: () => apiGet<CurrentUser>('/auth/me'),
  changePassword: (body: { currentPassword: string; newPassword: string }) => apiPut<void>('/auth/password', body),
}

export const dashboardApi = {
  overview: () => apiGet<DashboardOverview>('/dashboard'),
}

export const transactionsApi = {
  members: () => apiGet<MemberOption[]>('/family/members'),
  list: (params: Record<string, string | number | undefined>) => apiGet<PageResult<TransactionRecord>>('/transactions', { params }),
  summary: (params: Record<string, string | number | undefined>) => apiGet<TransactionSummary>('/transactions/summary', { params }),
  create: (body: TransactionPayload) => apiPost<TransactionRecord>('/transactions', body),
  update: (id: number, body: TransactionPayload) => apiPut<TransactionRecord>(`/transactions/${id}`, body),
  remove: (id: number) => apiDelete(`/transactions/${id}`),
}

export const familiesApi = {
  list: () => apiGet<Family[]>('/admin/families'),
  create: (body: { name: string; remark: string }) => apiPost<Family>('/admin/families', body),
  changeStatus: (id: number, active: boolean) => apiPut<void>(`/admin/families/${id}/status`, { active }),
}

export const auditApi = {
  list: (params: { familyId?: number; page: number; pageSize: number }) => apiGet<PageResult<AuditLog>>('/admin/audit-logs', { params }),
}
