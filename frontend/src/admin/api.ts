import { api } from '../lib/api'
import type { KeepAliveStatus } from '../lib/types'

export const adminKeys = {
  keepAlive: ['admin', 'keep-alive'] as const,
}

export const adminApi = {
  keepAlive: async () => (await api.get<KeepAliveStatus>('/api/admin/keep-alive')).data,
  setKeepAlive: async (enabled: boolean) =>
    (await api.put<KeepAliveStatus>('/api/admin/keep-alive', { enabled })).data,
}
