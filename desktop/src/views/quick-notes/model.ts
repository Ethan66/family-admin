import type { QuickNote, QuickNotePayload } from '@/types/quickNote'

/** 编辑副本只包含可提交字段，清单和图片数组不与列表共享引用。 */
export function createNoteDraft(note?: QuickNote): QuickNotePayload {
  if (!note) return { title: '', body: '', tags: [], imageIds: [], tasks: [], color: 'plain', pinned: false, status: 'ACTIVE', version: 0 }
  return {
    title: note.title, body: note.body, tags: [...note.tags], imageIds: [...note.imageIds],
    tasks: note.tasks.map((task) => ({ ...task })), color: note.color, pinned: note.pinned,
    status: note.status, version: note.version,
  }
}

export function formatNoteTime(value: string): string {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}
