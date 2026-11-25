import { useMutation } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { useConfirm } from '../components/confirm-context'
import { FormError } from '../components/form'
import { Icon } from '../components/Icon'
import { EmptyState, ErrorState, Spinner } from '../components/states'
import { button, card, muted } from '../components/ui'
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
  const confirm = useConfirm()

  const next = useMutation({ mutationFn: () => hostApi.next(pin), onSuccess: live.accept })
  const end = useMutation({ mutationFn: () => hostApi.end(pin), onSuccess: live.accept })
  const kick = useMutation({
    mutationFn: (playerId: number) => hostApi.removePlayer(pin, playerId),
    onSuccess: live.accept,
  })

  if (live.error && !live.state) {
    const backToQuizzes = (
      <Link to="/quizzes" className={button('secondary', 'sm')}>
        Về danh sách quiz
      </Link>
    )
    return (
      <LiveShell>
        {problemOf(live.error)?.status === 404 ? (
          <EmptyState
            icon="play"
            title="Không tìm thấy phòng này"
            description="Phòng có thể đã hết hạn. Mở lại quiz và bấm “Chơi trực tiếp”."
            action={backToQuizzes}
          />
        ) : (
          <ErrorState message="Không tải được phòng." onRetry={() => window.location.reload()} />
        )}
      </LiveShell>
    )
  }
  const s = live.state
  if (!s) {
    return (
      <LiveShell>
        <div role="status" className="flex flex-col items-center gap-3 py-12 text-ink-muted">
          <Spinner className="size-8" />
          Đang mở phòng…
        </div>
      </LiveShell>
    )
  }

  const confirmEnd = async () => {
    const ok = await confirm({
      title: 'Kết thúc trò chơi?',
      message: 'Học sinh sẽ thấy bảng xếp hạng cuối cùng.',
      confirmLabel: 'Kết thúc',
      danger: true,
    })
    if (ok) end.mutate()
  }

  const confirmKick = async (id: number, nickname: string) => {
    const ok = await confirm({ title: `Mời ${nickname} ra khỏi phòng?`, confirmLabel: 'Mời ra', danger: true })
    if (ok) kick.mutate(id)
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
      <header className={`${card} flex flex-wrap items-center justify-between gap-3`}>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-primary">Chơi trực tiếp</p>
          <h1 className="truncate text-2xl font-bold text-ink">{s.quizTitle}</h1>
          <p className={muted}>
            {s.question ? `Câu ${s.question.position + 1} / ${s.questionCount}` : `${s.questionCount} câu hỏi`} ·{' '}
            {s.players.length} người chơi
            {!live.connected && <span className="font-semibold text-warning"> · Đang kết nối lại…</span>}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {s.status !== 'ended' ? (
            <>
              <button
                type="button"
                className={button('ghostDanger')}
                disabled={end.isPending}
                onClick={() => void confirmEnd()}
              >
                Kết thúc
              </button>
              <button
                type="button"
                className={button('primary')}
                disabled={next.isPending || (s.status === 'lobby' && s.players.length === 0)}
                onClick={() => next.mutate()}
              >
                {nextLabel}
                <Icon name="next" className="size-4" />
              </button>
            </>
          ) : (
            <Link to="/quizzes" className={button('secondary')}>
              Về danh sách quiz
            </Link>
          )}
        </div>
      </header>
      {actionError != null && <FormError message={problemOf(actionError)?.detail ?? 'Thao tác không thành công.'} />}

      {s.status === 'lobby' && <Lobby state={s} onKick={(id, name) => void confirmKick(id, name)} />}
      {s.question && s.status === 'question' && (
        <OpenQuestion question={s.question} state={s} clockOffset={live.clockOffset} />
      )}
      {s.question && s.status === 'reveal' && <Revealed question={s.question} state={s} />}
      {s.status === 'ended' && (
        <section className="space-y-4">
          <h2 className="text-center text-title text-ink">Bảng xếp hạng</h2>
          <Leaderboard players={s.players} />
        </section>
      )}
    </LiveShell>
  )
}

function Lobby({ state, onKick }: { state: LiveState; onKick: (id: number, nickname: string) => void }) {
  return (
    <section className="space-y-6">
      <div className="space-y-2 rounded-lg bg-brand px-6 py-8 text-center text-white">
        <p>
          Vào <span className="font-semibold">{window.location.host}/play</span> và nhập mã PIN
        </p>
        <p className="font-mono text-6xl font-bold tracking-widest text-accent sm:text-7xl">{state.pin}</p>
      </div>
      {state.players.length === 0 ? (
        <p className="flex items-center justify-center gap-2 text-ink-muted">
          <Spinner className="size-4" />
          Đang chờ học sinh vào phòng…
        </p>
      ) : (
        <ul className="flex flex-wrap justify-center gap-2">
          {state.players.map((p) => (
            <li key={p.id}>
              <button
                type="button"
                title="Mời ra khỏi phòng"
                className="min-h-11 rounded-full border border-border bg-surface px-4 font-semibold text-ink shadow-card hover:border-danger hover:text-danger hover:line-through"
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
        <h2 className="flex-1 text-2xl font-bold text-ink sm:text-3xl">{question.prompt}</h2>
        <Countdown seconds={seconds} />
      </div>
      {question.kind === 'short_text' ? (
        <p className="text-center text-lg text-ink-muted">Học sinh gõ câu trả lời trên máy của mình.</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {question.options.map((label, i) => (
            <li
              key={i}
              className={`${optionColors[i % optionColors.length]} rounded-lg px-5 py-4 text-xl font-semibold text-white`}
            >
              {optionLetter(i)}. {label}
            </li>
          ))}
        </ul>
      )}
      <p className="text-center text-lg text-ink-muted">
        Đã trả lời: <span className="font-bold text-ink">{state.answeredCount}</span> / {state.players.length}
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
        <h2 className="text-2xl font-bold text-ink">{question.prompt}</h2>
        <p className="text-ink-muted">
          {reveal.correctCount} / {state.players.length} bạn trả lời đúng
        </p>
      </div>
      {question.kind === 'short_text' ? (
        <p className={`${card} text-xl text-ink`}>
          Đáp án: <span className="font-semibold text-success">{correctAnswerText(question)}</span>
        </p>
      ) : (
        <ul className="space-y-2">
          {question.options.map((label, i) => {
            const correct = reveal.correctOptions.includes(i)
            return (
              <li
                key={i}
                className={`space-y-2 rounded-md border-2 bg-surface p-4 shadow-card ${
                  correct ? 'border-success' : 'border-border opacity-70'
                }`}
              >
                <div className="flex justify-between gap-3 font-semibold">
                  <span className="flex items-center gap-2 text-ink">
                    {correct && <Icon name="check" className="size-5 text-success" />}
                    {optionLetter(i)}. {label}
                    {correct && <span className="sr-only">(đáp án đúng)</span>}
                  </span>
                  <span className="text-ink-muted">{reveal.optionCounts[i]}</span>
                </div>
                {/* The count is already in the text above, so the bar is decoration only. */}
                <div aria-hidden="true" className="h-2 rounded-full bg-page">
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
        <h3 className="font-semibold text-ink">Top 5</h3>
        <Leaderboard players={state.players} limit={5} />
      </div>
    </section>
  )
}
