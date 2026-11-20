import axios from 'axios'
import type { AnswerValue, AttemptResult, AttemptView } from '../lib/types'

/**
 * Separate client without the teacher interceptors: student calls must never carry a teacher's
 * Bearer token or trigger a session refresh when a teacher is logged in on the same browser.
 */
export const publicApi = axios.create({ baseURL: import.meta.env.VITE_API_URL ?? '' })

// sessionStorage: on a shared school computer the token disappears with the tab. The student
// can always continue by entering the codes again (the backend reuses the attempt).
const tokenKey = (attemptId: number) => `attempt-token:${attemptId}`
export const getToken = (attemptId: number) => sessionStorage.getItem(tokenKey(attemptId))

const headers = (attemptId: number) => ({ 'X-Attempt-Token': getToken(attemptId) ?? '' })

export const studentKeys = {
  attempt: (id: number) => ['attempt', id] as const,
  result: (id: number) => ['attempt', id, 'result'] as const,
}

export const studentApi = {
  start: async (publicCode: string, accessCode: string) => {
    const { data } = await publicApi.post<{ token: string; attempt: AttemptView }>(
      `/api/public/assignments/${encodeURIComponent(publicCode)}/start`,
      { accessCode },
    )
    sessionStorage.setItem(tokenKey(data.attempt.attemptId), data.token)
    return data.attempt
  },
  view: async (id: number) =>
    (await publicApi.get<AttemptView>(`/api/public/attempts/${id}`, { headers: headers(id) })).data,
  save: async (id: number, position: number, answer: AnswerValue | null) => {
    await publicApi.put(`/api/public/attempts/${id}/answers/${position}`, { answer }, { headers: headers(id) })
  },
  submit: async (id: number) =>
    (await publicApi.post<AttemptResult>(`/api/public/attempts/${id}/submit`, null, { headers: headers(id) })).data,
  result: async (id: number) =>
    (await publicApi.get<AttemptResult>(`/api/public/attempts/${id}/result`, { headers: headers(id) })).data,
}
