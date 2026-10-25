import { apiGet, apiPost, apiPut, apiDelete } from '@/api/client'
import type { MemberOption } from '@/types/api'
import type { PassbookPage, PassbookPayload, PassbookQuery, PassbookRecord } from '@/types/passbook'

export const passbooksApi = {
  apiMembers: () => apiGet<MemberOption[]>('/family/members'),
  apiList: (params: PassbookQuery) => apiGet<PassbookPage>('/passbooks', { params }),
  apiCreate: (body: PassbookPayload) => apiPost<PassbookRecord>('/passbooks', body),
  apiUpdate: (id: number, body: PassbookPayload) => apiPut<PassbookRecord>(`/passbooks/${id}`, body),
  apiRemove: (id: number) => apiDelete(`/passbooks/${id}`),
}
