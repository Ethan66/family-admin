export interface TaskPayload {
  title: string
  memberId: number
  taskDate: string
  taskTime: string | null
  category: string
  repeatDaily: boolean
  note: string | null
}

export interface TaskOccurrence extends TaskPayload {
  id: number
  memberName: string
  startDate: string
  completed: boolean
}
