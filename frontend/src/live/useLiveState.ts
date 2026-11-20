import { Client } from '@stomp/stompjs'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { LiveState } from '../lib/types'

/**
 * The backend pushes over a WebSocket. In production the frontend (Vercel) cannot proxy it, so
 * VITE_WS_URL points straight at the backend; in dev the Vite proxy forwards /ws.
 */
function webSocketUrl() {
  if (import.meta.env.VITE_WS_URL) return import.meta.env.VITE_WS_URL
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

export interface LiveConnection {
  state: LiveState | null
  error: unknown
  connected: boolean
  /** Server clock minus browser clock, in ms, so every screen shows the same countdown. */
  clockOffset: number
  /** For REST responses (e.g. after "next"): shown at once, ignored if a newer push already arrived. */
  accept: (state: LiveState) => void
}

/**
 * Loads the room with REST, then follows the pushes on /topic/live/{pin}. Pushes can arrive out
 * of order, so only a state with a higher version replaces the current one. After a reconnect the
 * state is loaded again, because pushes sent while offline are lost. `load` must be a stable
 * function (e.g. hostApi.get).
 */
export function useLiveState(pin: string, load: (pin: string) => Promise<LiveState>): LiveConnection {
  const [state, setState] = useState<LiveState | null>(null)
  const [error, setError] = useState<unknown>()
  const [connected, setConnected] = useState(false)
  const [clockOffset, setClockOffset] = useState(0)
  const version = useRef(-1)

  const accept = useCallback((next: LiveState) => {
    if (next.version <= version.current) return
    version.current = next.version
    setClockOffset(new Date(next.serverTime).getTime() - Date.now())
    setState(next)
  }, [])

  useEffect(() => {
    let active = true
    version.current = -1
    const refresh = () =>
      load(pin).then(
        (s) => active && accept(s),
        (e: unknown) => active && setError(e),
      )

    const client = new Client({
      brokerURL: webSocketUrl(),
      reconnectDelay: 2000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/live/${pin}`, (message) => accept(JSON.parse(message.body) as LiveState))
        void refresh()
      },
      onWebSocketClose: () => setConnected(false),
    })
    void refresh()
    client.activate()
    return () => {
      active = false
      void client.deactivate()
    }
  }, [pin, load, accept])

  return { state, error, connected, clockOffset, accept }
}

/** Seconds left before `endsAt`, updated a few times per second. */
export function useSecondsLeft(endsAt: string | undefined, clockOffset: number) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!endsAt) return
    const timer = window.setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(timer)
  }, [endsAt])
  if (!endsAt) return 0
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - (now + clockOffset)) / 1000))
}
