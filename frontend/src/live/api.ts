import { api } from '../lib/api'
import type { AnswerValue, LiveJoin, LiveMe, LiveState } from '../lib/types'
import { publicApi } from '../student/api'

/** Teacher side, with the logged-in session. */
export const hostApi = {
  create: async (quizId: number) => (await api.post<LiveState>('/api/live', { quizId })).data,
  get: async (pin: string) => (await api.get<LiveState>(`/api/live/${pin}`)).data,
  next: async (pin: string) => (await api.post<LiveState>(`/api/live/${pin}/next`)).data,
  end: async (pin: string) => (await api.post<LiveState>(`/api/live/${pin}/end`)).data,
  removePlayer: async (pin: string, playerId: number) =>
    (await api.delete<LiveState>(`/api/live/${pin}/players/${playerId}`)).data,
}

/** What a player keeps for one room. sessionStorage: a shared computer forgets it with the tab. */
export interface Seat {
  playerId: number
  nickname: string
  token: string
}

const seatKey = (pin: string) => `live-seat:${pin}`

export function loadSeat(pin: string): Seat | null {
  const raw = sessionStorage.getItem(seatKey(pin))
  return raw ? (JSON.parse(raw) as Seat) : null
}

export function forgetSeat(pin: string) {
  sessionStorage.removeItem(seatKey(pin))
}

const playerHeaders = (seat: Seat) => ({ 'X-Player-Token': seat.token })

/** Player side: public client, never carries a teacher's token. */
export const playerApi = {
  state: async (pin: string) =>
    (await publicApi.get<LiveState>(`/api/public/live/${encodeURIComponent(pin)}`)).data,
  join: async (pin: string, nickname: string): Promise<Seat> => {
    const { data } = await publicApi.post<LiveJoin>(`/api/public/live/${encodeURIComponent(pin)}/players`, {
      nickname,
    })
    const seat = { playerId: data.playerId, nickname: data.nickname, token: data.token }
    sessionStorage.setItem(seatKey(pin), JSON.stringify(seat))
    return seat
  },
  me: async (pin: string, seat: Seat) =>
    (await publicApi.get<LiveMe>(`/api/public/live/${pin}/me`, { headers: playerHeaders(seat) })).data,
  answer: async (pin: string, seat: Seat, position: number, answer: AnswerValue) => {
    await publicApi.post(`/api/public/live/${pin}/answers`, { position, answer }, { headers: playerHeaders(seat) })
  },
}
