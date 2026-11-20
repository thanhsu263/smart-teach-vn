import { useMutation } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card } from '../components/ui'
import { problemOf } from '../lib/api'
import type { LiveQuestion, LiveState } from '../lib/types'
import { hostApi } from './api'
import { correctAnswerText, optionColors, optionLetter } from './options'
import { Countdown, Leaderboard, LiveShell } from './parts'
import { useLiveState, useSecondsLeft } from './useLiveState'

/** /live/:pin – the teacher's screen, meant for the projector. */
export function HostPage() {
  const pin = useParams().pin ?? ''
  const live = useLiveState(pin, hostApi.get)

  const next = useMutation({ mutationFn: () => hostApi.next(pin), onSuccess: live.accept })
  const end = useMutation({ mutationFn: () => hostApi.end(pin), onSuccess: live.accept })
  const kick = useMutation({
    mutationFn: (playerId: number) => hostApi.removePlayer(pin, playerId),
    onSuccess: live.accept,
  })

  if (live.error && !live.state) {
    return (
      <LiveShell>
        <div className={`${card} space-y-3`}>
          <p className="text-slate-700">
            {problemOf(live.error)?.status === 404 ? 'Không tìm thấy phòng này (có thể đã hết hạn).' : 'Không tải được phòng.'}
          </p>
          <Link to="/quizzes" className="text-sm text-blue-600 hover:underline">
            Về danh sách quiz
          </Link>
        </div>
      </LiveShell>
    )
  }
  const s = live.state
  if (!s) {
    return (
      <LiveShell>
        <p className="text-slate-500">Đang tải…</p>
      </LiveShell>
    )
  }

  const actionError = next.error ?? end.error ?? kick.error
  const isLast = s.question != null && s.question.position + 1 >= s.questionCount
  const nextLabel = {
    lobby: 'Bắt đầu',
    question: 'Hiện đáp án',
    reveal: isLast ? 'Xem kết quả' : 'Câu tiếp theo',
    ended: '',
  }[s.status]

  return (
    <LiveShell wide>
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-blue-600">SmartTeach · Chơi trực tiếp</p>
          <h1 className="truncate text-2xl font-bold text-slate-900">{s.quizTitle}</h1>
          <p className="text-sm text-slate-500">
            {s.question ? `Câu ${s.question.position + 1} / ${s.questionCount}` : `${s.questionCount} câu hỏi`} ·{' '}
            {s.players.length} người chơi
            {!live.connected && <span className="text-amber-600"> · Đang kết nối lại…</span>}
          </p>
        </div>
        <div className="flex gap-2">
          {s.status !== 'ended' && (
            <>
              <button
                type="button"
                className={button.danger}
                disabled={end.isPending}
                onClick={() => window.confirm('Kết thúc trò chơi ngay bây giờ?') && end.mutate()}
              >
                Kết thúc
              </button>
              <button
                type="button"
                className={`${button.primary} px-5`}
                disabled={next.isPending || (s.status === 'lobby' && s.players.length === 0)}
                onClick={() => next.mutate()}
              >
                {nextLabel}
              </button>
            </>
          )}
          {s.status === 'ended' && (
            <Link to="/quizzes" className={button.secondary}>
              Về danh sách quiz
            </Link>
          )}
        </div>
      </header>
      {actionError != null && <FormError message={problemOf(actionError)?.detail ?? 'Thao tác không thành công.'} />}

      {s.status === 'lobby' && <Lobby state={s} onKick={(id, name) => window.confirm(`Mời ${name} ra khỏi phòng?`) && kick.mutate(id)} />}
      {s.question && s.status === 'question' && (
        <OpenQuestion question={s.question} state={s} clockOffset={live.clockOffset} />
      )}
      {s.question && s.status === 'reveal' && <Revealed question={s.question} state={s} />}
      {s.status === 'ended' && (
        <section className="space-y-4">
          <h2 className="text-center text-3xl font-bold text-slate-900">Bảng xếp hạng</h2>
          <Leaderboard players={s.players} />
        </section>
      )}
    </LiveShell>
  )
}

function Lobby({ state, onKick }: { state: LiveState; onKick: (id: number, nickname: string) => void }) {
  return (
    <section className="space-y-6">
      <div className={`${card} space-y-2 text-center`}>
        <p className="text-slate-600">
          Vào <span className="font-semibold text-slate-900">{window.location.host}/play</span> và nhập mã PIN
        </p>
        <p className="font-mono text-7xl font-bold tracking-widest text-blue-600">{state.pin}</p>
      </div>
      {state.players.length === 0 ? (
        <p className="text-center text-slate-500">Đang chờ học sinh vào phòng…</p>
      ) : (
        <ul className="flex flex-wrap justify-center gap-2">
          {state.players.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                title="Mời ra khỏi phòng"
                className="rounded-full bg-white px-4 py-2 font-medium text-slate-800 shadow-sm ring-1 ring-slate-200 hover:bg-red-50 hover:text-red-700 hover:line-through"
                onClick={() => onKick(p.id, p.nickname)}
              >
                {p.nickname}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function OpenQuestion({ question, state, clockOffset }: { question: LiveQuestion; state: LiveState; clockOffset: number }) {
  const seconds = useSecondsLeft(question.endsAt, clockOffset)
  return (
    <section className="space-y-6">
      <div className={`${card} flex items-center gap-4`}>
        <h2 className="flex-1 text-3xl font-bold text-slate-900">{question.prompt}</h2>
        <Countdown seconds={seconds} />
      </div>
      {question.kind === 'short_text' ? (
        <p className="text-center text-lg text-slate-600">Học sinh gõ câu trả lời trên máy của mình.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {question.options.map((label, i) => (
            <li key={i} className={`${optionColors[i % optionColors.length]} rounded-2xl px-5 py-4 text-xl font-semibold text-white`}>
              {optionLetter(i)}. {label}
            </li>
          ))}
        </ul>
      )}
      <p className="text-center text-lg text-slate-600">
        Đã trả lời: <span className="font-bold text-slate-900">{state.answeredCount}</span> / {state.players.length}
        {question.kind === 'multi' && ' · Câu nhiều đáp án'}
      </p>
    </section>
  )
}

function Revealed({ question, state }: { question: LiveQuestion; state: LiveState }) {
  const reveal = question.reveal
  if (!reveal) return null
  const most = Math.max(1, ...reveal.optionCounts)
  return (
    <section className="space-y-6">
      <div className={`${card} space-y-1`}>
        <h2 className="text-2xl font-bold text-slate-900">{question.prompt}</h2>
        <p className="text-slate-600">
          {reveal.correctCount} / {state.players.length} bạn trả lời đúng
        </p>
      </div>
      {question.kind === 'short_text' ? (
        <p className={`${card} text-xl`}>
          Đáp án: <span className="font-semibold text-emerald-700">{correctAnswerText(question)}</span>
        </p>
      ) : (
        <ul className="space-y-2">
          {question.options.map((label, i) => {
            const correct = reveal.correctOptions.includes(i)
            return (
              <li key={i} className={`${card} space-y-2 ${correct ? 'ring-2 ring-emerald-500' : 'opacity-70'}`}>
                <div className="flex justify-between gap-3 font-medium">
                  <span className="text-slate-900">
                    {correct ? '✓ ' : ''}
                    {optionLetter(i)}. {label}
                  </span>
                  <span className="text-slate-600">{reveal.optionCounts[i]}</span>
                </div>
                <div className="h-2 rounded-full bg-slate-100">
                  <div
                    className={`h-2 rounded-full ${optionColors[i % optionColors.length]}`}
                    style={{ width: `${(reveal.optionCounts[i] / most) * 100}%` }}
                  />
                </div>
              </li>
            )
          })}
        </ul>
      )}
      <div className="space-y-2">
        <h3 className="font-semibold text-slate-900">Top 5</h3>
        <Leaderboard players={state.players} limit={5} />
      </div>
    </section>
  )
}
