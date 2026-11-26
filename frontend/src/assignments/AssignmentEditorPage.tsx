import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { ConflictError, Field, FormError } from '../components/form'
import { PageHeader } from '../components/page'
import { EmptyState, ErrorState, Skeleton } from '../components/states'
import { button, card, label, link, muted, select, textarea } from '../components/ui'
import { isModifiedElsewhere, problemOf } from '../lib/api'
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
  if (assignment.isPending) {
    return (
      <div role="status" aria-label="Đang tải" className="space-y-4">
        <Skeleton className="h-9 w-56" />
        <Skeleton className="h-96 w-full" />
      </div>
    )
  }
  if (assignment.isError) {
    if (problemOf(assignment.error)?.status === 404) {
      return (
        <EmptyState
          icon="assignment"
          title="Không tìm thấy bài tập này"
          description="Bài tập có thể đã bị xoá, hoặc đường dẫn không đúng."
          action={
            <Link to="/assignments" className={button('secondary', 'sm')}>
              Về danh sách bài tập
            </Link>
          }
        />
      )
    }
    return <ErrorState message="Không tải được bài tập." onRetry={() => void assignment.refetch()} />
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

  const { register, handleSubmit, reset, setError, formState } = useForm<AssignmentForm>({
    resolver: zodResolver(assignmentSchema),
    defaultValues: toForm(assignment, params.get('classId') ?? undefined),
  })
  const { errors } = formState

  const save = useMutation({
    mutationFn: (form: AssignmentForm) =>
      assignment ? assignmentApi.update(assignment.id, toPayload(form), assignment.version) : assignmentApi.create(toPayload(form)),
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

  // 409 from optimistic locking: load the copy saved elsewhere (this tab's edits are dropped).
  const conflict = assignment !== undefined && isModifiedElsewhere(save.error)
  const reload = useMutation({
    mutationFn: () =>
      queryClient.fetchQuery({
        queryKey: assignmentKeys.detail(assignment!.id),
        queryFn: () => assignmentApi.get(assignment!.id),
        staleTime: 0,
      }),
    onSuccess: (fresh) => {
      reset(toForm(fresh))
      save.reset()
    },
  })

  const problem = problemOf(save.error)
  const saveError = !save.error || conflict
    ? undefined
    : problem?.errors
      ? undefined
      : problem?.status === 409
        ? (problem.detail ?? `Bạn đã có ${MAX_ASSIGNMENTS} bài tập.`)
        : problem?.status === 404
          ? 'Lớp hoặc quiz không còn tồn tại.'
          : 'Không lưu được bài tập.'

  const back = assignment
    ? { to: `/assignments/${assignment.id}`, label: assignment.title }
    : { to: '/assignments', label: 'Bài tập' }

  if (closed) {
    return (
      <EmptyState
        icon="assignment"
        title="Bài tập đã đóng"
        description="Bài tập đã đóng nên không sửa được nữa."
        action={
          <Link to={back.to} className={button('secondary', 'sm')}>
            Quay lại
          </Link>
        }
      />
    )
  }

  return (
    <form onSubmit={handleSubmit((form) => save.mutate(form))} className="space-y-6" noValidate>
      <PageHeader back={back} title={assignment ? 'Sửa bài tập' : 'Giao bài tập'} />

      <section className={`${card} space-y-5`}>
        {published ? (
          // Not registered: the form keeps the default class and quiz ids, which cannot change now.
          <p className="rounded-sm bg-primary-tint px-3 py-2 text-sm text-ink">
            Lớp <strong>{assignment?.className}</strong> · Đã giao bài nên không đổi được lớp và quiz; vẫn sửa được hạn
            nộp.
          </p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="classId"
              label="Lớp"
              error={errors.classId?.message}
              hint={
                classes.data?.length === 0 && (
                  <>
                    Chưa có lớp nào.{' '}
                    <Link to="/classes" className={link}>
                      Tạo lớp trước
                    </Link>
                  </>
                )
              }
            >
              <select
                id="classId"
                className={`${select} w-full`}
                aria-invalid={errors.classId ? true : undefined}
                {...register('classId')}
              >
                <option value="">— Chọn lớp —</option>
                {classes.data?.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.studentCount} HS)
                  </option>
                ))}
              </select>
            </SelectField>
            <SelectField
              id="quizId"
              label="Quiz"
              error={errors.quizId?.message}
              hint={
                quizzes.data?.length === 0 && (
                  <>
                    Chưa có quiz nào.{' '}
                    <Link to="/quizzes/new" className={link}>
                      Soạn quiz trước
                    </Link>
                  </>
                )
              }
            >
              <select
                id="quizId"
                className={`${select} w-full`}
                aria-invalid={errors.quizId ? true : undefined}
                {...register('quizId')}
              >
                <option value="">— Chọn quiz —</option>
                {quizzes.data?.map((q) => (
                  <option key={q.id} value={q.id}>
                    {q.title} ({q.questionCount} câu)
                  </option>
                ))}
              </select>
            </SelectField>
          </div>
        )}

        <Field id="title" label="Tiêu đề" error={errors.title?.message} {...register('title')} />

        <div className="space-y-1.5">
          <label htmlFor="description" className={label}>
            Hướng dẫn cho học sinh (không bắt buộc)
          </label>
          <textarea
            id="description"
            rows={3}
            className={textarea}
            aria-invalid={errors.description ? true : undefined}
            aria-describedby={errors.description ? 'description-error' : undefined}
            {...register('description')}
          />
          {errors.description && (
            <p id="description-error" className="text-sm text-danger">
              {errors.description.message}
            </p>
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            id="opensAt"
            type="datetime-local"
            label="Mở lúc"
            hint="Để trống: mở ngay khi giao."
            {...register('opensAt')}
          />
          <Field
            id="dueAt"
            type="datetime-local"
            label="Hạn nộp"
            hint="Để trống: không có hạn."
            error={errors.dueAt?.message}
            {...register('dueAt')}
          />
        </div>

        <fieldset className="space-y-2">
          <legend className={`${label} mb-2`}>Cho học sinh xem điểm và đáp án</legend>
          {(['after_close', 'immediate'] as const).map((policy) => (
            <label key={policy} className="flex min-h-11 items-center gap-3 text-ink">
              <input type="radio" className="size-5 accent-primary" value={policy} {...register('revealPolicy')} />
              {REVEAL_LABELS[policy]}
            </label>
          ))}
        </fieldset>
      </section>

      <FormError message={saveError} />
      {conflict && (
        <ConflictError
          message="Bài tập đã được sửa ở nơi khác (tab hoặc máy khác). Tải lại để xem bản mới nhất; thay đổi chưa lưu ở đây sẽ mất."
          onReload={() => reload.mutate()}
          reloading={reload.isPending}
        />
      )}
      <div className="flex flex-wrap gap-2">
        <button type="submit" className={button('primary')} disabled={save.isPending}>
          {save.isPending ? 'Đang lưu…' : assignment ? 'Lưu' : 'Tạo bản nháp'}
        </button>
        <Link to={back.to} className={button('secondary')}>
          Huỷ
        </Link>
      </div>
      {!assignment && (
        <p className={muted}>
          Bản nháp chưa hiện với học sinh. Bấm “Giao bài” ở trang tiếp theo để chốt câu hỏi và lấy mã bài.
        </p>
      )}
    </form>
  )
}

/** Label, a <select> passed as children, and an error or hint under it. */
function SelectField(props: { id: string; label: string; error?: string; hint?: ReactNode; children: ReactNode }) {
  const { id, error, hint, children } = props
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className={label}>
        {props.label}
      </label>
      {children}
      {error ? <p className="text-sm text-danger">{error}</p> : hint && <p className={muted}>{hint}</p>}
    </div>
  )
}
