export type Role = 'SUPER_ADMIN' | 'FAMILY_MEMBER'
export interface CurrentUser { id: number; familyId: number | null; username: string; displayName: string; role: Role }
export interface PageResult<T> { records: T[]; total: number; page: number; pageSize: number }
export interface Transaction { id: number; type: 'INCOME'|'EXPENSE'; amount: number; transactionDate: string; category: string; memberName: string; note?: string; createdBy: number }
export interface Exam { id: number; name: string; subject: string; grade: string; examDate: string; fullScore: number; score?: number; status: 'PENDING'|'REVIEWED'; reviewNote?: string; createdBy: number }
export interface Photo { id: number; title: string; album: string; photoDate: string; description?: string; fileId: number; createdBy: number }
export interface Movie { id: number; title: string; type: string; releaseYear: number; director?: string; minutes: number; rating?: number; watchStatus: 'PLANNED'|'WATCHING'|'FINISHED'; progress: number; description?: string; coverFileId?: number; createdBy: number }
export interface StudyNote { id: number; title: string; tag: string; markdownBody: string; createdBy: number; updatedAt: string }
export interface Family { id: number; name: string; status: string; remark?: string }
export interface DashboardOverview { income: number; expense: number; balance: number; examCount: number; photoCount: number; movieCount: number; noteCount: number }
