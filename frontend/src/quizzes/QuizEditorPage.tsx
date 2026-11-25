import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef } from 'react'
import { useFieldArray, useForm, useWatch, type UseFormReturn } from 'react-hook-form'
import { Link, useBlocker, useLocation, useNavigate, useParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card, input, select } from '../components/ui'
import { problemOf } from '../lib/api'
import type { QuestionKind, Quiz } from '../lib/types'
import { quizApi, quizKeys } from './api'
import {
  emptyOptions,
  KIND_LABELS,
  MAX_ANSWERS,
  MAX_OPTIONS,
  MAX_QUESTIONS,
  MAX_QUIZZES,
  newQuestion,
  type QuizForm,
  quizFormSchema,
  TIME_LIMITS,
  toForm,
  toPayload,
  trueFalseOptions,
} from './schemas'

export function QuizEditorPage() {
  const { id } = useParams()
  const quizId = id === undefined ? undefined : Number(id)
  const quiz = useQuery({
    queryKey: quizKeys.detail(quizId ?? 0),
    queryFn: () => quizApi.get(quizId!),
    enabled: quizId !== undefined,
  })

  if (quizId !== undefined) {
    if (quiz.isPending) return <p className="text-slate-500">Đang tải…</p>
    if (quiz.isError) {
      const status = problemOf(quiz.error)?.status
      return (
        <div className={`${card} space-y-3`}>
          <p className="text-slate-700">
            {status === 404 || status === 400 ? 'Không tìm thấy quiz này.' : 'Không tải được quiz.'}
          </p>
          <Link to="/quizzes" className="text-sm text-blue-600 hover:underline">
            ← Về danh sách quiz
          </Link>
        </div>
      )
    }
  }

  // key: a fresh form per quiz, so defaultValues are read again after create → edit.
  return <QuizEditor key={quizId ?? 'new'} quiz={quiz.data} />
}

function QuizEditor({ quiz }: { quiz?: Quiz }) {
  const navigate = useNavigate()
  const location = useLocation()
  const queryClient = useQueryClient()
  const leaving = useRef(false)

  const form = useForm<QuizForm>({ resolver: zodResolver(quizFormSchema), defaultValues: toForm(quiz) })
  const { control, register, handleSubmit, reset, setError, formState } = form
  const questions = useFieldArray({ control, name: 'questions' })

  useUnsavedChangesWarning(formState.isDirty, leaving)

  const save = useMutation({
    mutationFn: (values: QuizForm) =>
      quiz ? quizApi.update(quiz.id, toPayload(values)) : quizApi.create(toPayload(values)),
    onSuccess: (saved) => {
      queryClient.setQueryData(quizKeys.detail(saved.id), saved)
      void queryClient.invalidateQueries({ queryKey: quizKeys.lists })
      if (quiz) {
        reset(toForm(saved))
      } else {
        leaving.current = true
        navigate(`/quizzes/${saved.id}`, { replace: true, state: { created: true } })
      }
    },
    onError: (error) => {
      // Field errors from the server use paths like "questions[1].options"; show them in place.
      const errors = problemOf(error)?.errors ?? {}
      for (const [path, message] of Object.entries(errors)) {
        setError(path.replace(/\[(\d+)]/g, '.$1') as never, { type: 'server', message })
      }
    },
  })

  const invalidQuestions = Object.keys(formState.errors.questions ?? {})
    .filter((key) => /^\d+$/.test(key))
    .map((key) => Number(key) + 1)

  const saveError = (() => {
    if (invalidQuestions.length > 0) return `Còn lỗi ở câu ${invalidQuestions.join(', ')}.`
    if (!save.error) return undefined
    const status = problemOf(save.error)?.status
    if (status === 409) return `Bạn đã có ${MAX_QUIZZES} quiz, hãy xoá bớt quiz cũ.`
    if (status === 404) return 'Quiz này không còn tồn tại.'
    if (status === 400) return 'Quiz chưa hợp lệ, xem các lỗi bên dưới.'
    return 'Không lưu được quiz, vui lòng thử lại.'
  })()

  const justSaved = !formState.isDirty && (save.isSuccess || location.state?.created === true)

  return (
    <form className="space-y-4" noValidate onSubmit={handleSubmit((values) => save.mutate(values))}>
      <Link to="/quizzes" className="text-sm text-blue-600 hover:underline">
        ← Quiz
      </Link>

      <div className={`${card} sticky top-0 z-10 space-y-3`}>
        <div className="flex flex-wrap items-start gap-3">
          <div className="min-w-0 flex-1 space-y-1">
            <input
              aria-label="Tên quiz"
              placeholder="Tên quiz, ví dụ Ôn tập chương 1"
              aria-invalid={formState.errors.title ? true : undefined}
              className={`${input} text-lg font-semibold`}
              {...register('title')}
            />
            {formState.errors.title && <p className="text-sm text-red-600">{formState.errors.title.message}</p>}
          </div>
          <div className="flex items-center gap-3">
            <span className="text-sm text-slate-500" aria-live="polite">
              {justSaved ? 'Đã lưu' : formState.isDirty ? 'Chưa lưu' : ''}
            </span>
            <button type="submit" className={button('primary')} disabled={save.isPending}>
              {save.isPending ? 'Đang lưu…' : 'Lưu quiz'}
            </button>
          </div>
        </div>
        <FormError message={saveError} />
        {arrayError(formState.errors.questions) && <FormError message={arrayError(formState.errors.questions)} />}
      </div>

      {questions.fields.length === 0 && (
        <div className={`${card} text-center text-slate-500`}>Quiz chưa có câu hỏi nào.</div>
      )}

      <ol className="space-y-4">
        {questions.fields.map((field, index) => (
          <QuestionCard
            key={field.id}
            form={form}
            index={index}
            count={questions.fields.length}
            onMove={(to) => questions.move(index, to)}
            onRemove={() => questions.remove(index)}
          />
        ))}
      </ol>

      <button
        type="button"
        className={`${button('secondary')} w-full border-dashed`}
        disabled={questions.fields.length >= MAX_QUESTIONS}
        onClick={() => questions.append(newQuestion())}
      >
        + Thêm câu hỏi ({questions.fields.length}/{MAX_QUESTIONS})
      </button>
    </form>
  )
}

interface QuestionCardProps {
  form: UseFormReturn<QuizForm>
  index: number
  count: number
  onMove: (to: number) => void
  onRemove: () => void
}

function QuestionCard({ form, index, count, onMove, onRemove }: QuestionCardProps) {
  const { control, register, setValue, trigger, getValues, formState } = form
  const options = useFieldArray({ control, name: `questions.${index}.options` })
  const answers = useFieldArray({ control, name: `questions.${index}.acceptedAnswers` })
  const kind = useWatch({ control, name: `questions.${index}.kind` })
  const optionValues = useWatch({ control, name: `questions.${index}.options` })
  const errors = formState.errors.questions?.[index]
  const isText = kind === 'short_text'
  const isMulti = kind === 'multi'

  const revalidate = () => {
    if (formState.isSubmitted) void trigger(`questions.${index}`)
  }

  const changeKind = (next: QuestionKind, previous: QuestionKind) => {
    if (next === 'true_false') {
      options.replace(trueFalseOptions())
    } else if (previous === 'true_false' && next !== 'short_text') {
      options.replace(emptyOptions())
    } else if (next === 'single') {
      // A radio group can show only one checked option, so keep just the first correct one.
      const current = getValues(`questions.${index}.options`)
      const first = current.findIndex((o) => o.correct)
      options.replace(current.map((o, j) => ({ ...o, correct: j === first })))
    }
    if (next === 'short_text' && answers.fields.length === 0) answers.append({ value: '' }, { shouldFocus: false })
    revalidate()
  }

  const markCorrect = (j: number, checked: boolean) => {
    optionValues.forEach((option, k) => {
      const value = isMulti ? (k === j ? checked : option.correct) : k === j
      setValue(`questions.${index}.options.${k}.correct`, value, { shouldDirty: true })
    })
    revalidate()
  }

  const kindField = register(`questions.${index}.kind`)

  return (
    <li className={`${card} space-y-4`}>
      <div className="flex flex-wrap items-center gap-2">
        <span className="mr-1 font-semibold text-slate-900">Câu {index + 1}</span>
        <select
          aria-label={`Loại câu ${index + 1}`}
          className={select}
          {...kindField}
          onChange={(event) => {
            const previous = kind
            void kindField.onChange(event)
            changeKind(event.target.value as QuestionKind, previous)
          }}
        >
          {Object.entries(KIND_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <select
          aria-label={`Thời gian câu ${index + 1}`}
          className={select}
          {...register(`questions.${index}.timeLimitSeconds`, { valueAsNumber: true })}
        >
          {TIME_LIMITS.map((seconds) => (
            <option key={seconds} value={seconds}>
              {seconds} giây
            </option>
          ))}
        </select>
        <div className="ml-auto flex gap-1">
          <button
            type="button"
            className={button('secondary')}
            aria-label={`Đưa câu ${index + 1} lên`}
            disabled={index === 0}
            onClick={() => onMove(index - 1)}
          >
            ↑
          </button>
          <button
            type="button"
            className={button('secondary')}
            aria-label={`Đưa câu ${index + 1} xuống`}
            disabled={index === count - 1}
            onClick={() => onMove(index + 1)}
          >
            ↓
          </button>
          <button
            type="button"
            className={button('ghostDanger')}
            onClick={() => {
              if (!getValues(`questions.${index}.prompt`).trim() || window.confirm(`Xoá câu ${index + 1}?`)) onRemove()
            }}
          >
            Xoá
          </button>
        </div>
      </div>

      <div className="space-y-1">
        <textarea
          aria-label={`Nội dung câu ${index + 1}`}
          placeholder="Nội dung câu hỏi"
          rows={2}
          aria-invalid={errors?.prompt ? true : undefined}
          className={input}
          {...register(`questions.${index}.prompt`)}
        />
        {errors?.prompt && <p className="text-sm text-red-600">{errors.prompt.message}</p>}
      </div>

      {!isText && (
        <fieldset className="space-y-2">
          <legend className="mb-2 text-sm text-slate-500">
            {isMulti ? 'Tick mọi đáp án đúng.' : 'Chọn một đáp án đúng.'}
          </legend>
          {options.fields.map((field, j) => (
            <div key={field.id} className="space-y-1">
              <div className="flex items-center gap-2">
                <input
                  type={isMulti ? 'checkbox' : 'radio'}
                  name={`correct-${index}`}
                  aria-label={`Lựa chọn ${j + 1} là đáp án đúng`}
                  className="size-5 shrink-0 accent-green-600"
                  checked={optionValues[j]?.correct ?? false}
                  onChange={(event) => markCorrect(j, event.target.checked)}
                />
                <input
                  aria-label={`Lựa chọn ${j + 1} của câu ${index + 1}`}
                  placeholder={`Lựa chọn ${j + 1}`}
                  aria-invalid={errors?.options?.[j]?.label ? true : undefined}
                  className={input}
                  {...register(`questions.${index}.options.${j}.label`)}
                />
                {kind !== 'true_false' && (
                  <button
                    type="button"
                    className={`${button('secondary')} shrink-0`}
                    aria-label={`Xoá lựa chọn ${j + 1}`}
                    disabled={options.fields.length <= 2}
                    onClick={() => {
                      options.remove(j)
                      revalidate()
                    }}
                  >
                    ×
                  </button>
                )}
              </div>
              {errors?.options?.[j]?.label && (
                <p className="pl-7 text-sm text-red-600">{errors.options[j].label.message}</p>
              )}
            </div>
          ))}
          {kind !== 'true_false' && options.fields.length < MAX_OPTIONS && (
            <button
              type="button"
              className="text-sm text-blue-600 hover:underline"
              onClick={() => options.append({ label: '', correct: false })}
            >
              + Thêm lựa chọn
            </button>
          )}
          {arrayError(errors?.options) && <p className="text-sm text-red-600">{arrayError(errors?.options)}</p>}
        </fieldset>
      )}

      {isText && (
        <fieldset className="space-y-2">
          <legend className="mb-2 text-sm text-slate-500">Đáp án được chấp nhận (không phân biệt hoa thường).</legend>
          {answers.fields.map((field, j) => (
            <div key={field.id} className="space-y-1">
              <div className="flex items-center gap-2">
                <input
                  aria-label={`Đáp án ${j + 1} của câu ${index + 1}`}
                  placeholder={`Đáp án ${j + 1}`}
                  aria-invalid={errors?.acceptedAnswers?.[j]?.value ? true : undefined}
                  className={input}
                  {...register(`questions.${index}.acceptedAnswers.${j}.value`)}
                />
                <button
                  type="button"
                  className={`${button('secondary')} shrink-0`}
                  aria-label={`Xoá đáp án ${j + 1}`}
                  disabled={answers.fields.length <= 1}
                  onClick={() => {
                    answers.remove(j)
                    revalidate()
                  }}
                >
                  ×
                </button>
              </div>
              {errors?.acceptedAnswers?.[j]?.value && (
                <p className="text-sm text-red-600">{errors.acceptedAnswers[j].value.message}</p>
              )}
            </div>
          ))}
          {answers.fields.length < MAX_ANSWERS && (
            <button
              type="button"
              className="text-sm text-blue-600 hover:underline"
              onClick={() => answers.append({ value: '' })}
            >
              + Thêm đáp án
            </button>
          )}
          {arrayError(errors?.acceptedAnswers) && (
            <p className="text-sm text-red-600">{arrayError(errors?.acceptedAnswers)}</p>
          )}
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" className="size-4" {...register(`questions.${index}.ignoreDiacritics`)} />
            Bỏ qua dấu khi chấm (“Ha Noi” cũng đúng với “Hà Nội”)
          </label>
        </fieldset>
      )}
    </li>
  )
}

/** Errors on a whole array (for example "mark one correct option") sit on .message or, from the resolver, .root. */
function arrayError(error: unknown): string | undefined {
  const e = error as { message?: string; root?: { message?: string } } | undefined
  return e?.message ?? e?.root?.message
}

/** Ask before leaving the editor (in-app link or closing the tab) when there are unsaved changes. */
function useUnsavedChangesWarning(dirty: boolean, leaving: { current: boolean }) {
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && !leaving.current && currentLocation.pathname !== nextLocation.pathname,
  )

  useEffect(() => {
    if (blocker.state !== 'blocked') return
    if (window.confirm('Quiz có thay đổi chưa lưu. Rời trang và bỏ các thay đổi?')) blocker.proceed()
    else blocker.reset()
  }, [blocker])

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => event.preventDefault()
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
}
