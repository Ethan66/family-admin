export type CourseStatus = 'PENDING' | 'ATTENDED' | 'LEAVE'
export type CourseColor = 'blue' | 'purple' | 'green' | 'cyan' | 'orange'
export interface CourseBalance {
  courseId: number | null
  title: string
  memberId: number
  memberName: string
  remainingLessons: number | null
  totalLessons: number | null
  editable: boolean
}
export interface CoursePayload {
  title: string
  memberId: number
  dayOfWeek: number
  startTime: string
  endTime: string
  color: CourseColor
}
export interface CourseOccurrence extends CoursePayload {
  id: number
  createdBy: number
  memberName: string
  date: string
  status: CourseStatus
  note: string
}
export interface CourseRecord {
  id: number
  courseId: number
  memberId: number
  memberName: string
  title: string
  courseDate: string
  startTime: string
  endTime: string
  status: Exclude<CourseStatus, 'PENDING'>
  note: string
  remainingLessons: number | null
  totalLessons: number | null
}
