import { api } from '../lib/api'
import type { Page, Quiz, QuizPayload, QuizSummary } from '../lib/types'

export const PAGE_SIZE = 12

export const quizKeys = {
  all: ['quizzes'] as const,
  lists: ['quizzes', 'list'] as const,
  list: (page: number) => ['quizzes', 'list', page] as const,
  detail: (id: number) => ['quizzes', 'detail', id] as const,
}

export const quizApi = {
  list: async (page: number) =>
    (await api.get<Page<QuizSummary>>('/api/quizzes', { params: { page, size: PAGE_SIZE } })).data,
  get: async (id: number) => (await api.get<Quiz>(`/api/quizzes/${id}`)).data,
  create: async (payload: QuizPayload) => (await api.post<Quiz>('/api/quizzes', payload)).data,
  update: async (id: number, payload: QuizPayload) => (await api.put<Quiz>(`/api/quizzes/${id}`, payload)).data,
  remove: async (id: number) => {
    await api.delete(`/api/quizzes/${id}`)
  },
  duplicate: async (id: number) => (await api.post<Quiz>(`/api/quizzes/${id}/duplicate`)).data,
}
