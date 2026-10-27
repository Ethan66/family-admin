import { apiDelete, apiGet, apiPost, apiPut, http } from '@/api/client'
import type { QuickNote, QuickNotePayload, NoteImageUpload } from '@/types/quickNote'

export const quickNotesApi = {
  apiList: () => apiGet<QuickNote[]>('/quick-notes'),
  apiCreate: (body: QuickNotePayload) => apiPost<QuickNote>('/quick-notes', body),
  apiUpdate: (id: number, body: QuickNotePayload) => apiPut<QuickNote>(`/quick-notes/${id}`, body),
  apiRemove: (id: number, version: number) => apiDelete(`/quick-notes/${id}?version=${version}`),
  apiUpload: (file: File) => {
    const tempBody = new FormData()
    tempBody.append('file', file)
    return apiPost<NoteImageUpload>('/files', tempBody)
  },
  // 图片通过带登录凭证的请求读取，避免在 URL 中暴露 token。
  apiImage: async (id: number, signal: AbortSignal) => {
    const res = await http.get<Blob>(`/files/${id}`, { responseType: 'blob', signal })
    return res.data
  },
}
