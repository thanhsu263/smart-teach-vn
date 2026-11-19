import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card, input, select } from '../components/ui'
import { problemOf } from '../lib/api'
import type { Assignment } from '../lib/types'
import { assignmentApi, assignmentKeys } from './api'
import { type AssignmentForm, assignmentSchema, MAX_ASSIGNMENTS, REVEAL_LABELS, toForm, toPayload } from './schemas'

/** /assignments/new (optionally ?classId=) and /assignments/:id/edit. */
export function AssignmentEditorPage() {
  const param = useParams().id
  const id = param ? Number(param) : null
  const assignment = useQuery({
    queryKey: assignmentKeys.detail(id ?? 0),
    queryFn: () => assignmentApi.get(id!),
    enabled: id !== null,
  })

  if (id === null) return <EditorForm />
  if (assignment.isPending) return <p className="text-slate-500">Đang tải…</p>
  if (assignment.isError) {
    return (
      <div className={`${card} space-y-3`}>
        <p className="text-slate-700">
          {problemOf(assignment.error)?.status === 404 ? 'Không tìm thấy bài tập này.' : 'Không tải được bài tập.'}
        </p>
        <Link to="/assignments" className="text-sm text-blue-600 hover:underline">
          ← Về danh sách bài tập
        </Link>
      </div>
    )
  }
  return <EditorForm assignment={assignment.data} />
}

function EditorForm({ assignment }: { assignment?: Assignment }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [params] = useSearchParams()
  const published = assignment?.status === 'published'
  const closed = assignment?.status === 'closed'

  const classes = useQuery({ queryKey: ['classes', 'all'], queryFn: assignmentApi.allClasses })
  const quizzes = useQuery({ queryKey: ['quizzes', 'all'], queryFn: assignmentApi.allQuizzes })

  const { register, handleSubmit, setError, formState } = useForm<AssignmentForm>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: toForm(assignment, params.get('classId') ?? undefined),
  })
  const { errors } = formState

  const save = useMutation({
    mutationFn: (form: AssignmentForm) =>
      assignment ? assignmentApi.update(assignment.id, toPayload(form)) : assignmentApi.create(toPayload(form)),
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: assignmentKeys.all })
      navigate(`/assignments/${saved.id}`)
    },
    onError: (error) => {
      for (const [field, message] of Object.entries(problemOf(error)?.errors ?? {})) {
        setError(field as keyof AssignmentForm, { type: 'server', message })
      }
    },
  })

  const problem = problemOf(save.error)
  const saveError = !save.error
    ? undefined
    : problem?.errors
      ? undefined
      : problem?.status === 409
        ? (problem.detail ?? `Bạn đã có ${MAX_ASSIGNMENTS} bài tập.`)
        : problem?.status === 404
          ? 'Lớp hoặc quiz không còn tồn tại.'
          : 'Không lưu được bài tập.'

  const back = assignment ? `/assignments/${assignment.id}` : '/assignments'

  if (closed) {
    return (
      <div className={`${card} space-y-3`}>
        <p className="text-slate-700">Bài tập đã đóng, không sửa được nữa.</p>
        <Link to={back} className="text-sm text-blue-600 hover:underline">
          ← Quay lại
        </Link>
      </div>
    )
  }

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(form))} className="space-y-6" noValidate>
      <Link to={back} className="text-sm text-blue-600 hover:underline">
        ← Quay lại
      </Link>
      <h1 className="text-2xl font-bold text-slate-900">{assignment ? 'Sửa bài tập' : 'Giao bài tập'}</h1>

      <section className={`${card} space-y-4`}>
        {published ? (
          // Not registered: the form keeps the default class and quiz ids, which cannot change now.
          <p className="text-sm text-slate-600">
            Lớp <strong>{assignment?.className}</strong> · Đã giao bài nên không đổi được lớp và quiz; vẫn sửa được hạn
            nộp.
          </p>
        ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="classId" className="block text-sm font-medium text-slate-700">
              Lớp
            </label>
            <select id="classId" className={`${select} w-full`} {...register('classId')}>
              <option value="">— Chọn lớp —</option>
              {classes.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.studentCount} HS)
                </option>
              ))}
            </select>
            {errors.classId && <p className="text-sm text-red-600">{errors.classId.message}</p>}
          </div>
          <div className="space-y-1">
            <label htmlFor="quizId" className="block text-sm font-medium text-slate-700">
              Quiz
            </label>
            <select id="quizId" className={`${select} w-full`} {...register('quizId')}>
              <option value="">— Chọn quiz —</option>
              {quizzes.data?.map((q) => (
                <option key={q.id} value={q.id}>
                  {q.title} ({q.questionCount} câu)
                </option>
              ))}
            </select>
            {errors.quizId && <p className="text-sm text-red-600">{errors.quizId.message}</p>}
          </div>
        </div>
        )}

        <div className="space-y-1">
          <label htmlFor="title" className="block text-sm font-medium text-slate-700">
            Tiêu đề
          </label>
          <input id="title" className={input} aria-invalid={errors.title ? true : undefined} {...register('title')} />
          {errors.title && <p className="text-sm text-red-600">{errors.title.message}</p>}
        </div>

        <div className="space-y-1">
          <label htmlFor="description" className="block text-sm font-medium text-slate-700">
            Hướng dẫn cho học sinh (không bắt buộc)
          </label>
          <textarea
            id="description"
            rows={3}
            className={input}
            aria-invalid={errors.description ? true : undefined}
            {...register('description')}
          />
          {errors.description && <p className="text-sm text-red-600">{errors.description.message}</p>}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div className="space-y-1">
            <label htmlFor="opensAt" className="block text-sm font-medium text-slate-700">
              Mở lúc (trống = mở ngay khi giao)
            </label>
            <input id="opensAt" type="datetime-local" className={input} {...register('opensAt')} />
          </div>
          <div className="space-y-1">
            <label htmlFor="dueAt" className="block text-sm font-medium text-slate-700">
              Hạn nộp (trống = không có hạn)
            </label>
            <input
              id="dueAt"
              type="datetime-local"
              className={input}
              aria-invalid={errors.dueAt ? true : undefined}
              {...register('dueAt')}
            />
            {errors.dueAt && <p className="text-sm text-red-600">{errors.dueAt.message}</p>}
          </div>
        </div>

        <fieldset className="space-y-2">
          <legend className="text-sm font-medium text-slate-700">Cho học sinh xem điểm và đáp án</legend>
          {(['after_close', 'immediate'] as const).map((policy) => (
            <label key={policy} className="flex items-center gap-2 text-sm text-slate-700">
              <input type="radio" value={policy} {...register('revealPolicy')} />
              {REVEAL_LABELS[policy]}
            </label>
          ))}
        </fieldset>
      </section>

      <FormError message={saveError} />
      <div className="flex gap-2">
        <button type="submit" className={button.primary} disabled={save.isPending}>
          {save.isPending ? 'Đang lưu…' : assignment ? 'Lưu' : 'Tạo bản nháp'}
        </button>
        <Link to={back} className={button.secondary}>
          Huỷ
        </Link>
      </div>
      {!assignment && (
        <p className="text-sm text-slate-500">
          Bản nháp chưa hiện với học sinh. Bấm “Giao bài” ở trang tiếp theo để chốt câu hỏi và lấy mã bài.
        </p>
      )}
    </form>
  )
}
