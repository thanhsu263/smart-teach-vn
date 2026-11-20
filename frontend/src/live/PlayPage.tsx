import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { button, card, input } from '../components/ui'
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
    <AuthCard title="Chơi quiz trực tiếp">
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
        <div className={`${card} space-y-3`}>
          <p className="text-slate-700">Phòng không còn tồn tại.</p>
          <button type="button" className={button.secondary} onClick={onLeave}>
            Vào phòng khác
          </button>
        </div>
      </LiveShell>
    )
  }
  if (!s) {
    return (
      <LiveShell>
        <p className="text-slate-500">Đang tải…</p>
      </LiveShell>
    )
  }

  const me = s.players.find((p) => p.id === seat.playerId)
  if (!me) {
    return (
      <LiveShell>
        <div className={`${card} space-y-3`}>
          <p className="text-slate-700">Thầy cô đã mời bạn ra khỏi phòng.</p>
          <button type="button" className={button.secondary} onClick={onLeave}>
            Vào phòng khác
          </button>
        </div>
      </LiveShell>
    )
  }

  return (
    <LiveShell>
      <header className="flex items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold text-blue-600">SmartTeach</p>
          <p className="truncate font-semibold text-slate-900">{me.nickname}</p>
        </div>
        <div className="text-right">
          <p className="text-2xl font-bold text-slate-900">{me.score}</p>
          <p className="text-xs text-slate-500">
            {s.question ? `Câu ${s.question.position + 1} / ${s.questionCount}` : 'điểm'}
            {!live.connected && ' · đang kết nối lại…'}
          </p>
        </div>
      </header>

      {s.status === 'lobby' && (
        <div className={`${card} space-y-2 text-center`}>
          <p className="text-lg font-semibold text-slate-900">Bạn đã vào phòng!</p>
          <p className="text-slate-600">Chờ thầy cô bắt đầu… ({s.players.length} người chơi)</p>
        </div>
      )}
      {s.status === 'question' && s.question && (
        <AnswerPanel key={s.question.position} pin={pin} seat={seat} question={s.question} clockOffset={live.clockOffset} />
      )}
      {s.status === 'reveal' && s.question && <RevealPanel question={s.question} state={s} playerId={seat.playerId} />}
      {s.status === 'ended' && (
        <section className="space-y-4">
          <div className={`${card} text-center`}>
            <p className="text-slate-600">Kết thúc! Bạn xếp hạng</p>
            <p className="text-5xl font-bold text-blue-600">#{me.rank}</p>
            <p className="text-slate-600">với {me.score} điểm</p>
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
        <p className="flex-1 text-lg font-semibold text-slate-900">{question.prompt}</p>
        <Countdown seconds={seconds} />
      </div>

      {sent ? (
        <div className={`${card} text-center text-slate-700`}>Đã gửi câu trả lời. Chờ đáp án…</div>
      ) : closed ? (
        <div className={`${card} text-center text-slate-700`}>Hết giờ!</div>
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
          <button type="submit" className={`${button.primary} w-full py-3`} disabled={disabled || !text.trim()}>
            Gửi
          </button>
        </form>
      ) : question.kind === 'multi' ? (
        <div className="space-y-3">
          <p className="text-sm text-slate-500">Chọn tất cả đáp án đúng rồi bấm Gửi.</p>
          <div className="grid grid-cols-2 gap-3">
            {question.options.map((label, i) => {
              const on = chosen.includes(i)
              return (
                <button
                  key={i}
                  type="button"
                  aria-pressed={on}
                  disabled={disabled}
                  className={`${optionColors[i % optionColors.length]} min-h-20 rounded-2xl p-3 text-left font-semibold text-white ${
                    on ? 'ring-4 ring-slate-900' : 'opacity-80'
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
            className={`${button.primary} w-full py-3`}
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
              className={`${optionColors[i % optionColors.length]} min-h-24 rounded-2xl p-3 text-left text-lg font-semibold text-white hover:brightness-110`}
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
      <div className={`${card} space-y-1 text-center ${right ? 'ring-2 ring-emerald-500' : 'ring-2 ring-red-300'}`}>
        <p className={`text-3xl font-bold ${right ? 'text-emerald-600' : points == null ? 'text-slate-700' : 'text-red-600'}`}>
          {right ? `Chính xác! +${points}` : points == null ? 'Hết giờ, bạn chưa trả lời' : 'Chưa đúng'}
        </p>
        <p className="text-slate-600">
          Đáp án: <span className="font-semibold text-slate-900">{correctAnswerText(question)}</span>
        </p>
        {me && <p className="text-sm text-slate-500">Bạn đang xếp hạng #{me.rank}</p>}
      </div>
      <Leaderboard players={state.players} limit={5} highlightId={playerId} />
    </section>
  )
}
