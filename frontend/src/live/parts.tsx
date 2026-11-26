import type { ReactNode } from 'react'
import { Logo } from '../components/form'
import type { LivePlayer } from '../lib/types'

export function LiveShell({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <main className="min-h-screen">
      <div className={`mx-auto space-y-5 px-4 py-5 sm:space-y-6 sm:px-6 sm:py-8 ${wide ? 'max-w-5xl' : 'max-w-lg'}`}>
        <Logo />
        {children}
      </div>
    </main>
  )
}

export function Countdown({ seconds }: { seconds: number }) {
  return (
    <span
      className={`grid size-14 shrink-0 place-items-center rounded-full text-2xl font-bold text-white ${
        seconds <= 5 ? 'bg-danger' : 'bg-primary'
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
    <ol className="divide-y divide-border overflow-hidden rounded-md border border-border bg-surface shadow-card">
      {shown.map((p) => (
        <li
          key={p.id}
          className={`flex items-center gap-3 px-4 py-2.5 ${p.id === highlightId ? 'bg-primary-tint font-semibold' : ''}`}
        >
          <span className="w-8 text-right text-ink-muted">{p.rank}</span>
          <span className="min-w-0 flex-1 truncate text-ink">{p.nickname}</span>
          {p.lastPoints != null && p.lastPoints > 0 && <span className="text-sm text-success">+{p.lastPoints}</span>}
          <span className="w-16 text-right font-semibold text-ink">{p.score}</span>
        </li>
      ))}
    </ol>
  )
}
