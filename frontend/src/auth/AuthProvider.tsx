import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, refreshSession, setAccessToken, setSessionExpiredHandler } from '../lib/api'
import type { AuthResponse, User } from '../lib/types'
import { AuthContext, type AuthState, type LoginInput, type RegisterInput } from './auth-context'

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  useEffect(() => {
    setSessionExpiredHandler(clearSession)
    // Restore the session after a page reload using the refresh cookie.
    refreshSession()
      .then((res) => setUser(res.user))
      .catch(() => setUser(null))
      .finally(() => setLoading(false))
    return () => setSessionExpiredHandler(null)
  }, [clearSession])

  const startSession = useCallback((data: AuthResponse) => {
    setAccessToken(data.accessToken)
    setUser(data.user)
  }, [])

  const login = useCallback(
    async (input: LoginInput) => startSession((await api.post<AuthResponse>('/api/auth/login', input)).data),
    [startSession],
  )

  const register = useCallback(
    async (input: RegisterInput) => startSession((await api.post<AuthResponse>('/api/auth/register', input)).data),
    [startSession],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      clearSession()
    }
  }, [clearSession])

  const value = useMemo<AuthState>(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
