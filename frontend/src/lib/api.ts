import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse, ProblemDetail } from './types'

// Empty in dev (Vite proxy); the backend URL in production.
const baseURL = import.meta.env.VITE_API_URL ?? ''

export const api = axios.create({ baseURL, withCredentials: true })

// Access token lives only in memory; the refresh token is an HttpOnly cookie JS cannot read.
let accessToken: string | null = null
let onSessionExpired: (() => void) | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function setSessionExpiredHandler(handler: (() => void) | null) {
  onSessionExpired = handler
}

const isAuthUrl = (url?: string) => url?.startsWith('/api/auth/') ?? false

api.interceptors.request.use((config) => {
  if (accessToken && !isAuthUrl(config.url)) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

let refreshing: Promise<AuthResponse> | null = null

/**
 * Single-flight: parallel 401s share one refresh call. Sending the same refresh token twice
 * would look like token reuse to the backend and revoke the whole session.
 */
export function refreshSession(): Promise<AuthResponse> {
  refreshing ??= api
    .post<AuthResponse>('/api/auth/refresh')
    .then((res) => {
      setAccessToken(res.data.accessToken)
      return res.data
    })
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

type RetryableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as RetryableConfig | undefined
    if (error.response?.status !== 401 || !original || original._retried || isAuthUrl(original.url)) {
      throw error
    }
    original._retried = true
    try {
      await refreshSession()
    } catch {
      setAccessToken(null)
      onSessionExpired?.()
      throw error
    }
    return api(original)
  },
)

export function problemOf(error: unknown): ProblemDetail | undefined {
  return axios.isAxiosError<ProblemDetail>(error) ? error.response?.data : undefined
}

/** Optimistic locking: the backend refuses a save made from an older copy with this 409. */
export function isModifiedElsewhere(error: unknown): boolean {
  const problem = problemOf(error)
  return problem?.status === 409 && problem.detail?.startsWith('Modified elsewhere') === true
}

/** Message for a 429 from the rate limiter, or undefined for any other error. */
export function tooManyRequestsMessage(error: unknown): string | undefined {
  if (!axios.isAxiosError(error) || error.response?.status !== 429) return undefined
  const seconds = Number(error.response.headers['retry-after'])
  const wait = seconds > 0 ? (seconds < 60 ? `${seconds} giây` : `${Math.ceil(seconds / 60)} phút`) : 'một lát'
  return `Bạn thao tác quá nhanh, hãy thử lại sau ${wait}.`
}
