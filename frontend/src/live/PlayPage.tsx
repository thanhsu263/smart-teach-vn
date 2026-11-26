import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { Icon } from '../components/Icon'
import { ColdStartHint, EmptyState, Spinner } from '../components/states'
import { button, card, input, muted } from '../components/ui'
import { problemOf } from '../lib/api'
import type { AnswerValue, LiveQuestion, LiveState } from '../lib/types'
import { forgetSeat, loadSeat, playerApi, type Seat } from './api'
import { correctAnswerText, optionColors, optionLetter } from './options'
import { Countdown, Leaderboard, LiveShell } from './parts'
import { useLiveState, useSecondsLeft } from './useLiveState'

const MAX_TEXT_ANSWER = 200

/** /play and /play/:pin – students join with the PIN and a nickname, no account needed. */
export function PlayPage() {
  const pin = useParams().pin ?? ''
  const [seat, setSeat] = useState(() => (pin ? loadSeat(pin) : null))

  if (!pin || !seat) return <JoinForm pin={pin} onJoined={setSeat} />
  return (
    <Game
      key={pin}
      pin={pin}
      seat={seat}
      onLeave={() => {
        forgetSeat(pin)
        setSeat(null)
      }}
    />
  )
}

function JoinForm({ pin: initialPin, onJoined }: { pin: string; onJoined: (seat: Seat) => void }) {
  const navigate = useNavigate()
  const [pin, setPin] = useState(initialPin)
  const [nickname, setNickname] = useState('')

  const join = useMutation({
    mutationFn: () => playerApi.join(pin.trim(), nickname),
    onSuccess: (seat) => {
      onJoined(seat)
      navigate(`/play/${pin.trim()}`, { replace: true })
    },
  })

  const problem = problemOf(join.error)
  const error = !join.error
    ? undefined
    : problem?.status === 404
      ? 'Không có phòng nào với mã PIN này.'
      : problem?.status === 400
        ? 'Biệt danh phải có từ 1 đến 20 ký tự.'
        : problem?.detail?.includes('taken')
          ? 'Biệt danh này đã có người dùng, hãy chọn tên khác.'
          : problem?.detail?.includes('full')
            ? 'Phòng đã đủ người.'
            : problem?.detail?.includes('started') || problem?.detail?.includes('ended')
              ? 'Trò chơi đã bắt đầu, không vào thêm được.'
              : 'Không vào được phòng, hãy thử lại.'

  const valid = /^\d{6}$/.test(pin.trim()) && nickname.trim().length > 0 && nickname.trim().length <= 20

  return (
    <AuthCard title="Chơi quiz trực tiếp" description="Nhập mã PIN trên màn hình của thầy cô.">
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          if (valid) join.mutate()
        }}
      >
        <FormError message={error} />
        <Field
          id="pin"
          label="Mã PIN (6 số)"
          inputMode="numeric"
          autoComplete="off"
          maxLength={6}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))}
        />
        <Field
          id="nickname"
          label="Biệt danh"
          autoComplete="off"
          maxLength={20}
          autoFocus={initialPin !== ''}
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
        />
        <SubmitButton pending={join.isPending}>Vào phòng</SubmitButton>
        {join.isPending && <ColdStartHint />}
      </form>
    </AuthCard>
  )
}

function Game({ pin, seat, onLeave }: { pin: string; seat: Seat; onLeave: () => void }) {
  const live = useLiveState(pin, playerApi.state)
  const s = live.state

  if (live.error && !s) {
    return (
      <LiveShell>
        <EmptyState
          icon="play"
          title="Phòng không còn tồn tại"
          description="Trò chơi có thể đã kết thúc. Hỏi thầy cô mã PIN mới."
          action={
            <button type="button" className={button('primary', 'sm')} onClick={onLeave}>
              Vào phòng khác
            </button>
          }
        />
      </LiveShell>
    )
  }
  if (!s) {
    return (
      <LiveShell>
        <div role="status" className="flex flex-col items-center gap-3 py-12 text-ink-muted">
          <Spinner className="size-8" />
          Đang vào phòng…
          <ColdStartHint />
        </div>
      </LiveShell>
    )
  }

  const me = s.players.find((p) => p.id === seat.playerId)
  if (!me) {
    return (
      <LiveShell>
        <EmptyState
          icon="users"
          title="Bạn đã được mời ra khỏi phòng"
          action={
            <button type="button" className={button('primary', 'sm')} onClick={onLeave}>
              Vào phòng khác
            </button>
          }
        />
      </LiveShell>
    )
  }

  return (
    <LiveShell>
      <header className={`${card} flex items-center justify-between gap-3`}>
        <div className="min-w-0">
          <p className={muted}>Biệt danh</p>
          <p className="truncate font-semibold text-ink">{me.nickname}</p>
        </div>
        <div className="text-right">
          <p className="text-2xl font-bold text-ink">{me.score}</p>
          <p className="text-xs text-ink-muted">
            {s.question ? `Câu ${s.question.position + 1} / ${s.questionCount}` : 'điểm'}
            {!live.connected && ' · đang kết nối lại…'}
          </p>
        </div>
      </header>

      {s.status === 'lobby' && (
        <div className={`${card} space-y-2 text-center`}>
          <p className="text-heading text-ink">Bạn đã vào phòng!</p>
          <p className="text-ink-muted">Chờ thầy cô bắt đầu… ({s.players.length} người chơi)</p>
        </div>
      )}
      {s.status === 'question' && s.question && (
        <AnswerPanel key={s.question.position} pin={pin} seat={seat} question={s.question} clockOffset={live.clockOffset} />
      )}
      {s.status === 'reveal' && s.question && <RevealPanel question={s.question} state={s} playerId={seat.playerId} />}
      {s.status === 'ended' && (
        <section className="space-y-4">
          <div className={`${card} text-center`}>
            <p className="text-ink-muted">Kết thúc! Bạn xếp hạng</p>
            <p className="text-display text-primary">#{me.rank}</p>
            <p className="text-ink-muted">với {me.score} điểm</p>
          </div>
          <Leaderboard players={s.players} limit={10} highlightId={seat.playerId} />
        </section>
      )}
    </LiveShell>
  )
}

function AnswerPanel({
  pin,
  seat,
  question,
  clockOffset,
}: {
  pin: string
  seat: Seat
  question: LiveQuestion
  clockOffset: number
}) {
  const seconds = useSecondsLeft(question.endsAt, clockOffset)
  const [sent, setSent] = useState(false)
  const [chosen, setChosen] = useState<number[]>([])
  const [text, setText] = useState('')

  // After a reload during a question, ask the server whether this player already answered.
  useEffect(() => {
    let active = true
    playerApi.me(pin, seat).then(
      (me) => active && me.answered && setSent(true),
      () => undefined,
    )
    return () => {
      active = false
    }
  }, [pin, seat])

  const send = useMutation({
    mutationFn: (answer: AnswerValue) => playerApi.answer(pin, seat, question.position, answer),
    onSuccess: () => setSent(true),
    onError: (error) => {
      if (problemOf(error)?.detail === 'Already answered') setSent(true)
    },
  })

  const closed = seconds === 0
  const disabled = send.isPending || closed

  return (
    <section className="space-y-4">
      <div className={`${card} flex items-center gap-4`}>
        <h1 className="flex-1 text-lg font-semibold text-ink">{question.prompt}</h1>
        <Countdown seconds={seconds} />
      </div>

      {sent ? (
        <div role="status" className={`${card} flex items-center justify-center gap-2 text-ink`}>
          <Icon name="check" className="text-success" />
          Đã gửi câu trả lời. Chờ đáp án…
        </div>
      ) : closed ? (
        <div role="status" className={`${card} text-center font-semibold text-ink`}>
          Hết giờ!
        </div>
      ) : question.kind === 'short_text' ? (
        <form
          className="space-y-3"
          onSubmit={(e) => {
            e.preventDefault()
            if (text.trim()) send.mutate(text)
          }}
        >
          <input
            aria-label="Câu trả lời"
            className={input}
            maxLength={MAX_TEXT_ANSWER}
            autoFocus
            value={text}
            onChange={(e) => setText(e.target.value)}
          />
          <button type="submit" className={`${button('primary')} w-full`} disabled={disabled || !text.trim()}>
            Gửi
          </button>
        </form>
      ) : question.kind === 'multi' ? (
        <div className="space-y-3">
          <p className={muted}>Chọn tất cả đáp án đúng rồi bấm Gửi.</p>
          <div className="grid grid-cols-2 gap-3">
            {question.options.map((label, i) => {
              const on = chosen.includes(i)
              return (
                <button
                  key={i}
                  type="button"
                  aria-pressed={on}
                  disabled={disabled}
                  className={`${optionColors[i % optionColors.length]} min-h-20 rounded-lg p-3 text-left font-semibold text-white ${
                    on ? 'ring-4 ring-ink ring-offset-2' : 'opacity-80'
                  }`}
                  onClick={() => setChosen(on ? chosen.filter((x) => x !== i) : [...chosen, i].sort((a, b) => a - b))}
                >
                  {on ? '✓ ' : ''}
                  {optionLetter(i)}. {label}
                </button>
              )
            })}
          </div>
          <button
            type="button"
            className={`${button('primary')} w-full`}
            disabled={disabled || chosen.length === 0}
            onClick={() => send.mutate(chosen)}
          >
            Gửi
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-2 gap-3">
          {question.options.map((label, i) => (
            <button
              key={i}
              type="button"
              disabled={disabled}
              className={`${optionColors[i % optionColors.length]} min-h-24 rounded-lg p-3 text-left text-lg font-semibold text-white hover:brightness-110 disabled:opacity-60`}
              onClick={() => send.mutate(i)}
            >
              {optionLetter(i)}. {label}
            </button>
          ))}
        </div>
      )}
      {send.isError && !sent && <FormError message={problemOf(send.error)?.status === 409 ? 'Câu hỏi đã đóng.' : 'Không gửi được, hãy thử lại.'} />}
    </section>
  )
}

function RevealPanel({ question, state, playerId }: { question: LiveQuestion; state: LiveState; playerId: number }) {
  const me = state.players.find((p) => p.id === playerId)
  const points = me?.lastPoints ?? null
  const right = points != null && points > 0
  return (
    <section className="space-y-4">
      <div
        className={`space-y-1 rounded-md border-2 bg-surface p-5 text-center shadow-card ${
          right ? 'border-success' : 'border-danger/40'
        }`}
      >
        <p className={`text-3xl font-bold ${right ? 'text-success' : points == null ? 'text-ink' : 'text-danger'}`}>
          {right ? `Chính xác! +${points}` : points == null ? 'Hết giờ, bạn chưa trả lời' : 'Chưa đúng'}
        </p>
        <p className="text-ink-muted">
          Đáp án: <span className="font-semibold text-ink">{correctAnswerText(question)}</span>
        </p>
        {me && <p className={muted}>Bạn đang xếp hạng #{me.rank}</p>}
      </div>
      <Leaderboard players={state.players} limit={5} highlightId={playerId} />
    </section>
  )
}
