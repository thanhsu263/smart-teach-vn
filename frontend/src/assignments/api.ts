import { api } from '../lib/api'
import type { Analytics, Assignment, AssignmentPayload, AssignmentSummary, ClassItem, Page, QuizSummary } from '../lib/types'

export const PAGE_SIZE = 12
/** Backend max-page-size; a teacher has fewer classes and quizzes than this. */
const ALL = 100

export const assignmentKeys = {
  all: ['assignments'] as const,
  list: (classId: number | null, page: number) => ['assignments', 'list', classId, page] as const,
  detail: (id: number) => ['assignments', 'detail', id] as const,
  analytics: (id: number) => ['assignments', 'analytics', id] as const,
}

export const assignmentApi = {
  list: async (classId: number | null, page: number) =>
    (
      await api.get<Page<AssignmentSummary>>('/api/assignments', {
        params: { classId: classId ?? undefined, page, size: PAGE_SIZE },
      })
    ).data,
  get: async (id: number) => (await api.get<Assignment>(`/api/assignments/${id}`)).data,
  create: async (payload: AssignmentPayload) => (await api.post<Assignment>('/api/assignments', payload)).data,
  update: async (id: number, payload: AssignmentPayload) =>
    (await api.put<Assignment>(`/api/assignments/${id}`, payload)).data,
  remove: async (id: number) => {
    await api.delete(`/api/assignments/${id}`)
  },
  publish: async (id: number) => (await api.post<Assignment>(`/api/assignments/${id}/publish`)).data,
  close: async (id: number) => (await api.post<Assignment>(`/api/assignments/${id}/close`)).data,
  analytics: async (id: number) => (await api.get<Analytics>(`/api/assignments/${id}/analytics`)).data,

  // Options for the class and quiz selects.
  allClasses: async () =>
    (await api.get<Page<ClassItem>>('/api/classes', { params: { size: ALL } })).data.content,
  allQuizzes: async () =>
    (await api.get<Page<QuizSummary>>('/api/quizzes', { params: { size: ALL } })).data.content,
}

export const statusLabel = { draft: 'Nháp', published: 'Đã giao', closed: 'Đã đóng' } as const

export const statusClass = {
  draft: 'bg-slate-100 text-slate-700',
  published: 'bg-green-100 text-green-800',
  closed: 'bg-amber-100 text-amber-800',
} as const

/** Link the teacher shares with the class; the student still needs their own access code. */
export const studentLink = (publicCode: string) => `${window.location.origin}/s/${publicCode}`
