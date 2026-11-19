import { z } from 'zod'
import type { Assignment, AssignmentPayload } from '../lib/types'

export const MAX_ASSIGNMENTS = 100

// Selects and datetime-local inputs give strings; they become ids and ISO instants in toPayload.
export const assignmentSchema = z
  .object({
    classId: z.string().min(1, 'Chọn lớp'),
    quizId: z.string().min(1, 'Chọn quiz'),
    title: z.string().trim().min(1, 'Nhập tiêu đề').max(100, 'Tối đa 100 ký tự'),
    description: z.string().max(1000, 'Tối đa 1000 ký tự'),
    opensAt: z.string(),
    dueAt: z.string(),
    revealPolicy: z.enum(['immediate', 'after_close']),
  })
  .refine((v) => !v.opensAt || !v.dueAt || new Date(v.dueAt) > new Date(v.opensAt), {
    path: ['dueAt'],
    message: 'Hạn nộp phải sau thời điểm mở',
  })

export type AssignmentForm = z.infer<typeof assignmentSchema>

/** ISO instant -> "yyyy-MM-ddTHH:mm" in the browser's time zone, the format datetime-local expects. */
export function toLocalInput(iso: string | null): string {
  if (!iso) return ''
  const date = new Date(iso)
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 16)
}

const toInstant = (local: string) => (local ? new Date(local).toISOString() : null)

export function toForm(assignment?: Assignment, classId?: string): AssignmentForm {
  return {
    classId: assignment ? String(assignment.classId) : (classId ?? ''),
    quizId: assignment?.quizId ? String(assignment.quizId) : '',
    title: assignment?.title ?? '',
    description: assignment?.description ?? '',
    opensAt: toLocalInput(assignment?.opensAt ?? null),
    dueAt: toLocalInput(assignment?.dueAt ?? null),
    revealPolicy: assignment?.revealPolicy ?? 'after_close',
  }
}

export function toPayload(form: AssignmentForm): AssignmentPayload {
  return {
    classId: Number(form.classId),
    quizId: Number(form.quizId),
    title: form.title,
    description: form.description,
    opensAt: toInstant(form.opensAt),
    dueAt: toInstant(form.dueAt),
    revealPolicy: form.revealPolicy,
  }
}

export const REVEAL_LABELS = {
  after_close: 'Sau khi hết hạn / đóng bài',
  immediate: 'Ngay sau khi nộp',
} as const
