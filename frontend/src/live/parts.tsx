import type { ReactNode } from 'react'
import type { LivePlayer } from '../lib/types'

export function LiveShell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <main className="min-h-screen bg-slate-50">
      <div className={`mx-auto space-y-6 p-4 sm:p-6 ${wide ? 'max-w-5xl' : 'max-w-lg'}`}>{children}</div>
    </main>
  )
}

export function Countdown({ seconds }: { seconds: number }) {
  return (
    <span
      className={`grid size-14 shrink-0 place-items-center rounded-full text-2xl font-bold text-white ${
        seconds <= 5 ? 'bg-red-500' : 'bg-blue-600'
      }`}
      aria-label={`Còn ${seconds} giây`}
    >
      {seconds}
    </span>
  )
}

export function Leaderboard({ players, limit, highlightId }: { players: LivePlayer[]; limit?: number; highlightId?: number }) {
  const shown = limit ? players.slice(0, limit) : players
  if (shown.length === 0) return null
  return (
    <ol className="divide-y divide-slate-100 overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
      {shown.map((p) => (
        <li
          key={p.id}
          className={`flex items-center gap-3 px-4 py-2 ${p.id === highlightId ? 'bg-blue-50 font-semibold' : ''}`}
        >
          <span className="w-8 text-right text-slate-500">{p.rank}</span>
          <span className="min-w-0 flex-1 truncate text-slate-900">{p.nickname}</span>
          {p.lastPoints != null && p.lastPoints > 0 && (
            <span className="text-sm text-emerald-600">+{p.lastPoints}</span>
          )}
          <span className="w-16 text-right font-semibold text-slate-900">{p.score}</span>
        </li>
      ))}
    </ol>
  )
}
