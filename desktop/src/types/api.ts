export type Role = 'SUPER_ADMIN' | 'FAMILY_MEMBER'

export interface CurrentUser {
  id: number
  familyId: number | null
  username: string
  displayName: string
  role: Role
}

export interface LoginResponse {
  accessToken: string
  user: CurrentUser
}

export interface ApiEnvelope<T> {
  code: string
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

export interface DashboardOverview {
  income: number
  expense: number
  balance: number
  examCount: number
  photoCount: number
  movieCount: number
  noteCount: number
}

export interface TransactionRecord {
  id: number
  type: 'INCOME' | 'EXPENSE'
  amount: number
  transactionDate: string
  category: string
  memberName: string
  note: string | null
  createdBy: number
  createdAt: string
}

export interface TransactionSummary {
  income: number
  expense: number
  balance: number
  incomeCount: number
  expenseCount: number
}

export interface TransactionPayload {
  type: 'INCOME' | 'EXPENSE'
  amount: number
  transactionDate: string
  category: string
  memberName: string
  note: string | null
}

export interface Family {
  id: number
  name: string
  status: string
  remark: string | null
}

export interface AuditLog {
  id: number
  familyId: number | null
  operatorId: number
  action: string
  resourceType: string
  resourceId: string | null
  result: string
  createdAt: string
}
