import { useQuery, useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card, input } from '../components/ui'
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
    <main className="min-h-screen bg-slate-50">
      <div className="mx-auto max-w-2xl space-y-6 p-4 sm:p-6">
        <p className="text-sm font-semibold text-blue-600">SmartTeach</p>
        {children}
      </div>
    </main>
  )
}

function Attempt({ id }: { id: number }) {
  const view = useQuery({ queryKey: studentKeys.attempt(id), queryFn: () => studentApi.view(id) })
  const [result, setResult] = useState<AttemptResult | null>(null)

  if (view.isPending) return <p className="text-slate-500">Đang tải…</p>
  if (view.isError) {
    return (
      <div className={`${card} space-y-3`}>
        <p className="text-slate-700">
          {problemOf(view.error)?.status === 404
            ? 'Phiên làm bài không còn hiệu lực (có thể thầy cô đã cấp mã mới).'
            : 'Không tải được bài, hãy thử lại.'}
        </p>
        <Link to="/s" className="text-sm text-blue-600 hover:underline">
          Nhập lại mã
        </Link>
      </div>
    )
  }
  if (result || view.data.status !== 'in_progress') return <ResultView id={id} initial={result} />
  return <AnswerSheet view={view.data} onSubmitted={setResult} />
}

function AnswerSheet({ view, onSubmitted }: { view: AttemptView; onSubmitted: (result: AttemptResult) => void }) {
  const queryClient = useQueryClient()
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
    const question = unanswered > 0 ? `Còn ${unanswered} câu chưa trả lời. Vẫn nộp bài?` : 'Nộp bài? Sau khi nộp không sửa được nữa.'
    if (!window.confirm(question)) return

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
        <h1 className="text-2xl font-bold text-slate-900">{view.assignmentTitle}</h1>
        <p className="text-sm text-slate-500">
          {view.studentName}
          {view.dueAt && ` · Hạn nộp ${dateFormat.format(new Date(view.dueAt))}`}
        </p>
        {view.description && <p className="whitespace-pre-line pt-2 text-slate-700">{view.description}</p>}
      </header>

      {locked && <FormError message="Bài tập chưa mở hoặc đã đóng nên không lưu được câu trả lời." />}

      <ol className="space-y-4">
        {view.questions.map((q) => (
          <li key={q.position} className={`${card} space-y-3`}>
            <div className="flex justify-between gap-3">
              <p className="font-medium text-slate-900">
                Câu {q.position + 1}. {q.prompt}
              </p>
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

      <div className={`${card} flex flex-wrap items-center justify-between gap-3`}>
        <p className="text-sm text-slate-600">
          Đã trả lời {answeredCount} / {view.questions.length} câu · Câu trả lời được lưu tự động.
        </p>
        <button type="button" className={button.primary} disabled={locked || submitting} onClick={() => void submit()}>
          {submitting ? 'Đang nộp…' : 'Nộp bài'}
        </button>
      </div>
      <FormError message={submitError} />
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
      {multi && <p className="text-xs text-slate-500">Chọn tất cả đáp án đúng.</p>}
      {q.options.map((label, index) => (
        <label
          key={index}
          className="flex cursor-pointer items-center gap-3 rounded-lg border border-slate-200 px-3 py-2 hover:bg-slate-50 has-checked:border-blue-400 has-checked:bg-blue-50"
        >
          <input
            type={multi ? 'checkbox' : 'radio'}
            name={`q-${q.position}`}
            disabled={disabled}
            checked={multi ? chosen.includes(index) : value === index}
            onChange={() => (multi ? toggle(index) : onChange(index))}
          />
          <span className="text-slate-800">{label}</span>
        </label>
      ))}
    </div>
  )
}

function SaveBadge({ status }: { status?: SaveStatus }) {
  if (!status) return null
  const text = { saving: 'Đang lưu…', saved: 'Đã lưu', error: 'Chưa lưu được' }[status]
  return (
    <span className={`shrink-0 text-xs ${status === 'error' ? 'text-red-600' : 'text-slate-400'}`}>{text}</span>
  )
}

function ResultView({ id, initial }: { id: number; initial: AttemptResult | null }) {
  const result = useQuery({
    queryKey: studentKeys.result(id),
    queryFn: () => studentApi.result(id),
    initialData: initial ?? undefined,
  })

  if (result.isPending) return <p className="text-slate-500">Đang tải kết quả…</p>
  if (result.isError) return <FormError message="Không tải được kết quả." />
  const r = result.data

  return (
    <>
      <header className={`${card} space-y-1`}>
        <h1 className="text-2xl font-bold text-slate-900">{r.assignmentTitle}</h1>
        <p className="text-sm text-slate-500">
          {r.studentName}
          {r.submittedAt && ` · Đã nộp lúc ${dateFormat.format(new Date(r.submittedAt))}`}
        </p>
        {r.status === 'invalidated' && <p className="text-sm text-red-600">Bài làm này đã bị huỷ.</p>}
      </header>

      {!r.revealed ? (
        <div className={`${card} text-slate-700`}>
          Em đã nộp bài. Điểm và đáp án sẽ hiện khi bài tập kết thúc – hãy mở lại link này sau.
        </div>
      ) : (
        <>
          <div className={`${card} text-center`}>
            <p className="text-sm text-slate-500">Điểm</p>
            <p className="text-5xl font-bold text-blue-600">{r.score}</p>
            <p className="text-sm text-slate-500">
              Đúng {r.correctCount} / {r.questionTotal} câu
            </p>
          </div>
          <ol className="space-y-3">
            {r.questions.map((q) => (
              <li key={q.position} className={`${card} space-y-1 text-sm`}>
                <p className="font-medium text-slate-900">
                  {q.correct ? '✓' : '✗'} Câu {q.position + 1}. {q.prompt}
                </p>
                <p className={q.correct ? 'text-green-700' : 'text-red-600'}>Em trả lời: {formatAnswer(q)}</p>
                {!q.correct && <p className="text-slate-600">Đáp án: {correctAnswer(q)}</p>}
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
