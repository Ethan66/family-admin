import type { CSSProperties } from 'vue'

const DAILY_TASK_MEMBER_PALETTE = [
  { background: '#e8f5f1', text: '#286653', accent: '#33856d' },
  { background: '#fcecf0', text: '#963d60', accent: '#c74f79' },
  { background: '#eaf2ff', text: '#355d99', accent: '#3976dc' },
  { background: '#f1ecfa', text: '#70559a', accent: '#8251be' },
  { background: '#fff1e3', text: '#965d2b', accent: '#c66b13' },
  { background: '#e7f4f8', text: '#316579', accent: '#398298' },
  { background: '#eef0f7', text: '#535f89', accent: '#6976a3' },
  { background: '#f3f2e4', text: '#6c692e', accent: '#888337' },
] as const

/** 按成员 ID 固定配色，切月、筛选及完成任务都不改变成员底色。 */
export function getDailyTaskMemberStyle(memberId: number): CSSProperties {
  const tempColor = DAILY_TASK_MEMBER_PALETTE[memberId % DAILY_TASK_MEMBER_PALETTE.length]!
  return {
    '--task-member-background': tempColor.background,
    '--task-member-text': tempColor.text,
    '--task-member-accent': tempColor.accent,
  }
}
