import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { useConfirm } from '../components/confirm-context'
import { FormError, Logo } from '../components/form'
import { Icon } from '../components/Icon'
import { EmptyState, ErrorState, Skeleton } from '../components/states'
import { badge, button, card, input, muted } from '../components/ui'
import { problemOf } from '../lib/api'
import type { AnswerValue, AttemptResult, AttemptView } from '../lib/types'
import { getToken, studentApi, studentKeys } from './api'

const MAX_TEXT_ANSWER = 200
const TEXT_SAVE_DELAY_MS = 800
const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

type Answers = Record<number, AnswerValue | null>
type SaveStatus = 'saving' | 'saved' | 'error'

export function AttemptPage() {
  const id = Number(useParams().id)
  // Without a token every call would be a 404; send the student back to enter the codes.
  if (!getToken(id)) return <Navigate to="/s" replace />
  return (
    <StudentShell>
      <Attempt id={id} />
    </StudentShell>
  )
}

export function StudentShell({ children }: { children: ReactNode }) {
  return (
    <main className="min-h-screen">
      <div className="mx-auto max-w-2xl space-y-5 px-4 py-5 sm:space-y-6 sm:px-6 sm:py-8">
        <Logo />
        {children}
      </div>
    </main>
  )
}

function Attempt({ id }: { id: number }) {
  const view = useQuery({ queryKey: studentKeys.attempt(id), queryFn: () => studentApi.view(id) })
  const [result, setResult] = useState<AttemptResult | null>(null)

  if (view.isPending) return <SheetSkeleton />
  if (view.isError) {
    if (problemOf(view.error)?.status === 404) {
      return (
        <EmptyState
          icon="assignment"
          title="Phiên làm bài không còn hiệu lực"
          description="Có thể thầy cô đã cấp mã mới cho em. Hãy nhập lại mã bài và mã học sinh."
          action={
            <Link to="/s" className={button('primary', 'sm')}>
              Nhập lại mã
            </Link>
          }
        />
      )
    }
    return <ErrorState message="Không tải được bài, hãy thử lại." onRetry={() => void view.refetch()} />
  }
  if (result || view.data.status !== 'in_progress') return <ResultView id={id} initial={result} />
  return <AnswerSheet view={view.data} onSubmitted={setResult} />
}

function SheetSkeleton() {
  return (
    <div role="status" aria-label="Đang tải" className="space-y-4">
      <div className={`${card} space-y-3`}>
        <Skeleton className="h-7 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
      </div>
      {[0, 1].map((i) => (
        <div key={i} className={`${card} space-y-3`}>
          <Skeleton className="h-5 w-2/3" />
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      ))}
    </div>
  )
}

function AnswerSheet({ view, onSubmitted }: { view: AttemptView; onSubmitted: (result: AttemptResult) => void }) {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const [answers, setAnswers] = useState<Answers>(() => view.answers as Answers)
  const [status, setStatus] = useState<Record<number, SaveStatus>>({})
  const [locked, setLocked] = useState(!view.open)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string>()

  // Saves of one question run one after another, so an older answer never overwrites a newer one.
  const queues = useRef(new Map<number, Promise<void>>())
  const latestSave = useRef(new Map<number, number>())
  const textTimers = useRef(new Map<number, { timer: number; value: AnswerValue | null }>())

  useEffect(() => {
    const timers = textTimers.current
    return () => timers.forEach(({ timer }) => clearTimeout(timer))
  }, [])

  const persist = (position: number, value: AnswerValue | null) => {
    const saveId = (latestSave.current.get(position) ?? 0) + 1
    latestSave.current.set(position, saveId)
    const isLatest = () => latestSave.current.get(position) === saveId
    setStatus((s) => ({ ...s, [position]: 'saving' }))

    const previous = queues.current.get(position) ?? Promise.resolve()
    const next = previous
      .then(() => studentApi.save(view.attemptId, position, value))
      .then(
        () => {
          if (isLatest()) setStatus((s) => ({ ...s, [position]: 'saved' }))
        },
        (error: unknown) => {
          if (isLatest()) setStatus((s) => ({ ...s, [position]: 'error' }))
          if (problemOf(error)?.status === 409) setLocked(true) // closed or past the due date
        },
      )
    queues.current.set(position, next)
  }

  const change = (position: number, value: AnswerValue | null, delay = 0) => {
    setAnswers((a) => ({ ...a, [position]: value }))
    const waiting = textTimers.current.get(position)
    if (waiting) clearTimeout(waiting.timer)
    if (delay === 0) {
      textTimers.current.delete(position)
      persist(position, value)
      return
    }
    const timer = window.setTimeout(() => {
      textTimers.current.delete(position)
      persist(position, value)
    }, delay)
    textTimers.current.set(position, { timer, value })
  }

  const submit = async () => {
    const unanswered = view.questions.filter((q) => answers[q.position] == null).length
    const ok = await confirm({
      title: unanswered > 0 ? `Còn ${unanswered} câu chưa trả lời. Vẫn nộp bài?` : 'Nộp bài?',
      message: 'Sau khi nộp, em không sửa được câu trả lời nữa.',
      confirmLabel: 'Nộp bài',
    })
    if (!ok) return

    setSubmitting(true)
    setSubmitError(undefined)
    // Send text answers that are still waiting for the autosave delay, then wait for every save.
    textTimers.current.forEach(({ timer, value }, position) => {
      clearTimeout(timer)
      persist(position, value)
    })
    textTimers.current.clear()
    await Promise.all(queues.current.values())
    try {
      onSubmitted(await studentApi.submit(view.attemptId))
      await queryClient.invalidateQueries({ queryKey: studentKeys.attempt(view.attemptId) })
    } catch (error) {
      const problem = problemOf(error)
      setSubmitError(problem?.status === 409 ? 'Bài tập đã đóng, không nộp được nữa.' : 'Không nộp được, hãy thử lại.')
      if (problem?.status === 409) setLocked(true)
    } finally {
      setSubmitting(false)
    }
  }

  const answeredCount = view.questions.filter((q) => answers[q.position] != null).length

  return (
    <>
      <header className={`${card} space-y-1`}>
        <h1 className="text-2xl font-bold text-ink">{view.assignmentTitle}</h1>
        <p className={muted}>
          {view.studentName}
          {view.dueAt && ` · Hạn nộp ${dateFormat.format(new Date(view.dueAt))}`}
        </p>
        {view.description && <p className="pt-2 whitespace-pre-line text-ink">{view.description}</p>}
      </header>

      {locked && <FormError message="Bài tập chưa mở hoặc đã đóng nên không lưu được câu trả lời." />}

      <ol className="space-y-4">
        {view.questions.map((q) => (
          <li key={q.position} className={`${card} space-y-4`}>
            <div className="flex items-start justify-between gap-3">
              <h2 className="font-semibold text-ink">
                <span className="text-primary">Câu {q.position + 1}.</span> {q.prompt}
              </h2>
              <SaveBadge status={status[q.position]} />
            </div>
            <QuestionInput
              question={q}
              value={answers[q.position] ?? null}
              disabled={locked}
              onChange={(value, delay) => change(q.position, value, delay)}
            />
          </li>
        ))}
      </ol>

      <FormError message={submitError} />
      {/* Stays at the bottom of the screen, so progress and "Nộp bài" are in reach from any question. */}
      <div className="sticky bottom-3 z-[5] flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-surface p-4 shadow-raised">
        <p className="text-sm text-ink">
          <strong>
            {answeredCount} / {view.questions.length}
          </strong>{' '}
          câu đã trả lời
          <span className="block text-ink-muted">Câu trả lời được lưu tự động.</span>
        </p>
        <button type="button" className={button('primary')} disabled={locked || submitting} onClick={() => void submit()}>
          {submitting ? 'Đang nộp…' : 'Nộp bài'}
        </button>
      </div>
    </>
  )
}

function QuestionInput({
  question: q,
  value,
  disabled,
  onChange,
}: {
  question: AttemptView['questions'][number]
  value: AnswerValue | null
  disabled: boolean
  onChange: (value: AnswerValue | null, delay?: number) => void
}) {
  if (q.kind === 'short_text') {
    return (
      <input
        aria-label={`Trả lời câu ${q.position + 1}`}
        className={input}
        maxLength={MAX_TEXT_ANSWER}
        disabled={disabled}
        value={typeof value === 'string' ? value : ''}
        // An empty box clears the saved answer (null) instead of saving a blank text.
        onChange={(e) => onChange(e.target.value.trim() ? e.target.value : null, TEXT_SAVE_DELAY_MS)}
      />
    )
  }

  const multi = q.kind === 'multi'
  const chosen = Array.isArray(value) ? value : []
  const toggle = (index: number) => {
    const next = chosen.includes(index) ? chosen.filter((i) => i !== index) : [...chosen, index].sort((a, b) => a - b)
    onChange(next.length > 0 ? next : null)
  }

  return (
    <div className="space-y-2">
      {multi && <p className={muted}>Chọn tất cả đáp án đúng.</p>}
      {q.options.map((label, index) => (
        <label
          key={index}
          className="flex min-h-12 cursor-pointer items-center gap-3 rounded-sm border border-border px-4 py-2.5 transition-colors hover:bg-page has-checked:border-primary has-checked:bg-primary-tint has-disabled:cursor-not-allowed has-disabled:opacity-60"
        >
          <input
            type={multi ? 'checkbox' : 'radio'}
            name={`q-${q.position}`}
            className="size-5 shrink-0 accent-primary"
            disabled={disabled}
            checked={multi ? chosen.includes(index) : value === index}
            onChange={() => (multi ? toggle(index) : onChange(index))}
          />
          <span className="text-ink">{label}</span>
        </label>
      ))}
    </div>
  )
}

function SaveBadge({ status }: { status?: SaveStatus }) {
  if (!status) return null
  if (status === 'error') return <span className={badge('danger')}>Chưa lưu được</span>
  return (
    <span className="inline-flex shrink-0 items-center gap-1 text-xs text-ink-muted">
      {status === 'saved' && <Icon name="check" className="size-3.5 text-success" />}
      {status === 'saving' ? 'Đang lưu…' : 'Đã lưu'}
    </span>
  )
}

function ResultView({ id, initial }: { id: number; initial: AttemptResult | null }) {
  const result = useQuery({
    queryKey: studentKeys.result(id),
    queryFn: () => studentApi.result(id),
    initialData: initial ?? undefined,
  })

  if (result.isPending) return <SheetSkeleton />
  if (result.isError) return <ErrorState message="Không tải được kết quả." onRetry={() => void result.refetch()} />
  const r = result.data

  return (
    <>
      <header className={`${card} space-y-1`}>
        <h1 className="text-2xl font-bold text-ink">{r.assignmentTitle}</h1>
        <p className={muted}>
          {r.studentName}
          {r.submittedAt && ` · Đã nộp lúc ${dateFormat.format(new Date(r.submittedAt))}`}
        </p>
        {r.status === 'invalidated' && <p className="text-sm font-semibold text-danger">Bài làm này đã bị huỷ.</p>}
      </header>

      {!r.revealed ? (
        <EmptyState
          icon="check"
          title="Em đã nộp bài"
          description="Điểm và đáp án sẽ hiện khi bài tập kết thúc. Hãy mở lại link này sau."
        />
      ) : (
        <>
          <div className={`${card} text-center`}>
            <p className={muted}>Điểm</p>
            <p className="text-display text-primary">{r.score}</p>
            <p className={muted}>
              Đúng {r.correctCount} / {r.questionTotal} câu
            </p>
          </div>
          <ol className="space-y-3">
            {r.questions.map((q) => (
              <li
                key={q.position}
                className={`flex gap-3 rounded-md border bg-surface p-4 text-sm shadow-card ${
                  q.correct ? 'border-success/30' : 'border-danger/30'
                }`}
              >
                <span
                  className={`grid size-7 shrink-0 place-items-center rounded-full ${
                    q.correct ? 'bg-success/10 text-success' : 'bg-danger/10 text-danger'
                  }`}
                >
                  <Icon name={q.correct ? 'check' : 'close'} className="size-4" />
                  <span className="sr-only">{q.correct ? 'Đúng' : 'Sai'}</span>
                </span>
                <div className="min-w-0 space-y-1">
                  <p className="font-semibold text-ink">
                    Câu {q.position + 1}. {q.prompt}
                  </p>
                  <p className={q.correct ? 'text-success' : 'text-danger'}>Em trả lời: {formatAnswer(q)}</p>
                  {!q.correct && <p className="text-ink">Đáp án: {correctAnswer(q)}</p>}
                </div>
              </li>
            ))}
          </ol>
        </>
      )}
    </>
  )
}

type ResultQuestion = AttemptResult['questions'][number]

function formatAnswer(q: ResultQuestion): string {
  const answer = q.answer
  if (answer === null) return '(bỏ trống)'
  if (typeof answer === 'string') return answer
  const indexes = Array.isArray(answer) ? answer : [answer]
  return indexes.map((i) => q.options[i]?.label).join(', ')
}

function correctAnswer(q: ResultQuestion): string {
  return q.kind === 'short_text'
    ? q.acceptedAnswers.join(' / ')
    : q.options
        .filter((o) => o.correct)
        .map((o) => o.label)
        .join(', ')
}
