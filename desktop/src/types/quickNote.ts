export type NoteStatus = 'ACTIVE' | 'ARCHIVED'
export type NoteColor = 'plain' | 'sage' | 'sand'

export interface NoteTask {
  text: string
  done: boolean
}

export interface QuickNotePayload {
  title: string
  body: string
  tags: string[]
  imageIds: number[]
  tasks: NoteTask[]
  color: NoteColor
  pinned: boolean
  status: NoteStatus
  version: number
}

export interface QuickNote extends QuickNotePayload {
  id: number
  createdBy: number
  createdAt: string
  updatedAt: string
}

export interface NoteImageUpload {
  id: number
  originalName: string
  storageKey: string
  mediaType: string
  sizeBytes: number
}
