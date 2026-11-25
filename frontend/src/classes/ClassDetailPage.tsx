import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import type { z } from 'zod'
import { useConfirm } from '../components/confirm-context'
import { FormError } from '../components/form'
import { Icon } from '../components/Icon'
import { PageHeader } from '../components/page'
import { EmptyState, ErrorState, QueryState, Skeleton, SkeletonList } from '../components/states'
import { button, card, input, muted, panel, textarea } from '../components/ui'
import { problemOf } from '../lib/api'
import type { ClassItem, Student, StudentCode } from '../lib/types'
import { classApi, classKeys } from './api'
import { NewCodesPanel } from './NewCodesPanel'
import { classNameSchema, MAX_STUDENTS, type NameForm, studentListSchema, studentNameSchema } from './schemas'

export function ClassDetailPage() {
  const classId = Number(useParams().id)
  // Plain codes exist only in this state: once the panel is closed they cannot be shown again.
  const [newCodes, setNewCodes] = useState<StudentCode[]>([])

  const classroom = useQuery({ queryKey: classKeys.detail(classId), queryFn: () => classApi.get(classId) })
  const students = useQuery({ queryKey: classKeys.students(classId), queryFn: () => classApi.students(classId) })

  if (classroom.isPending) {
    return (
      <div role="status" aria-label="Đang tải" className="space-y-6">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-9 w-48" />
        <Skeleton className="h-64 w-full" />
      </div>
    )
  }
  if (classroom.isError) {
    if (problemOf(classroom.error)?.status === 404) {
      return (
        <EmptyState
          icon="classes"
          title="Không tìm thấy lớp này"
          description="Lớp có thể đã bị xoá, hoặc đường dẫn không đúng."
          action={
            <Link to="/classes" className={button('secondary', 'sm')}>
              Về danh sách lớp
            </Link>
          }
        />
      )
    }
    return <ErrorState message="Không tải được lớp." onRetry={() => void classroom.refetch()} />
  }

  return (
    <div className="space-y-6">
      <ClassHeader classroom={classroom.data} />

      {newCodes.length > 0 && <NewCodesPanel codes={newCodes} onClose={() => setNewCodes([])} />}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <section className={panel} aria-labelledby="students-heading">
          <h2 id="students-heading" className="border-b border-border px-5 py-4 text-heading text-ink">
            Học sinh ({classroom.data.studentCount})
          </h2>
          <QueryState
            query={students}
            error="Không tải được danh sách học sinh."
            loading={<SkeletonList rows={2} />}
            isEmpty={(data) => data.length === 0}
            empty={
              <p className={`${muted} px-5 py-8 text-center`}>
                Lớp chưa có học sinh. Dán danh sách tên vào ô “Thêm học sinh” để tạo mã cho cả lớp.
              </p>
            }
          >
            {(data) => (
              <ol className="divide-y divide-border">
                {data.map((s, index) => (
                  <StudentRow
                    key={s.id}
                    index={index}
                    student={s}
                    classId={classId}
                    onNewCode={(code) => setNewCodes([code])}
                  />
                ))}
              </ol>
            )}
          </QueryState>
        </section>

        <AddStudentsForm
          classId={classId}
          remaining={MAX_STUDENTS - classroom.data.studentCount}
          onAdded={setNewCodes}
        />
      </div>
    </div>
  )
}

function ClassHeader({ classroom }: { classroom: ClassItem }) {
  const [editing, setEditing] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const confirm = useConfirm()

  const remove = useMutation({
    mutationFn: () => classApi.remove(classroom.id),
    onSuccess: () => {
      // Drop this class's cached data instead of refetching it (it would only return 404 now).
      queryClient.removeQueries({ queryKey: classKeys.detail(classroom.id) })
      queryClient.removeQueries({ queryKey: classKeys.students(classroom.id) })
      void queryClient.invalidateQueries({ queryKey: classKeys.lists })
      navigate('/classes', { replace: true })
    },
  })

  const confirmDelete = async () => {
    const ok = await confirm({
      title: `Xoá lớp "${classroom.name}"?`,
      message: 'Học sinh và kết quả bài tập của lớp cũng bị xoá. Không thể hoàn tác.',
      confirmLabel: 'Xoá lớp',
      danger: true,
    })
    if (ok) remove.mutate()
  }

  const back = { to: '/classes', label: 'Lớp học' }

  if (editing) {
    return (
      <PageHeader
        back={back}
        title={
          <NameEditor
            initial={classroom.name}
            schema={classNameSchema}
            label="Tên lớp"
            save={(name) => classApi.rename(classroom.id, name)}
            onDone={() => setEditing(false)}
          />
        }
      />
    )
  }

  return (
    <div className="space-y-3">
      <PageHeader
        back={back}
        title={classroom.name}
        description={`${classroom.studentCount} học sinh`}
        actions={
          <>
            <Link to={`/assignments?classId=${classroom.id}`} className={button('secondary', 'sm')}>
              <Icon name="assignment" className="size-4" />
              Bài tập của lớp
            </Link>
            <button type="button" className={button('secondary', 'sm')} onClick={() => setEditing(true)}>
              Đổi tên
            </button>
            <button
              type="button"
              className={button('ghostDanger', 'sm')}
              onClick={() => void confirmDelete()}
              disabled={remove.isPending}
            >
              Xoá lớp
            </button>
          </>
        }
      />
      {remove.isError && <FormError message="Không xoá được lớp, vui lòng thử lại." />}
    </div>
  )
}

function StudentRow(props: {
  index: number
  student: Student
  classId: number
  onNewCode: (code: StudentCode) => void
}) {
  const { index, student, classId, onNewCode } = props
  const [editing, setEditing] = useState(false)
  const queryClient = useQueryClient()
  const confirm = useConfirm()
  const refresh = () => queryClient.invalidateQueries({ queryKey: classKeys.all })

  const regenerate = useMutation({
    mutationFn: () => classApi.regenerateCode(student.id),
    onSuccess: (code) => {
      onNewCode(code)
      return queryClient.invalidateQueries({ queryKey: classKeys.students(classId) })
    },
  })
  const remove = useMutation({ mutationFn: () => classApi.removeStudent(student.id), onSuccess: refresh })

  const confirmRegenerate = async () => {
    const ok = await confirm({
      title: `Cấp mã mới cho ${student.name}?`,
      message: 'Mã cũ sẽ không dùng được nữa.',
      confirmLabel: 'Cấp mã mới',
    })
    if (ok) regenerate.mutate()
  }

  const confirmRemove = async () => {
    const ok = await confirm({
      title: `Xoá học sinh ${student.name}?`,
      message: 'Kết quả bài làm của học sinh này cũng bị xoá.',
      confirmLabel: 'Xoá',
      danger: true,
    })
    if (ok) remove.mutate()
  }

  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 px-5 py-3">
      <span className="w-6 shrink-0 text-sm text-ink-muted">{index + 1}</span>
      {editing ? (
        <div className="min-w-0 flex-1">
          <NameEditor
            initial={student.name}
            schema={studentNameSchema}
            label="Họ tên học sinh"
            save={(name) => classApi.renameStudent(student.id, name)}
            onDone={() => setEditing(false)}
          />
        </div>
      ) : (
        <>
          <div className="min-w-0 flex-1">
            <p className="truncate font-semibold text-ink">{student.name}</p>
            <p className="font-mono text-sm text-ink-muted" title="4 ký tự cuối của mã">
              Mã ••••{student.codeHint}
            </p>
          </div>
          <div className="flex gap-1 max-sm:w-full max-sm:pl-10">
            <button type="button" className={button('ghost', 'sm')} onClick={() => setEditing(true)}>
              Sửa
            </button>
            <button
              type="button"
              className={button('ghost', 'sm')}
              disabled={regenerate.isPending}
              onClick={() => void confirmRegenerate()}
            >
              Cấp lại mã
            </button>
            <button
              type="button"
              className={button('ghostDanger', 'sm')}
              disabled={remove.isPending}
              onClick={() => void confirmRemove()}
            >
              Xoá
            </button>
          </div>
        </>
      )}
    </li>
  )
}

/** Inline "rename" form shared by the class title and student rows. */
function NameEditor(props: {
  initial: string
  schema: typeof classNameSchema
  label: string
  save: (name: string) => Promise<unknown>
  onDone: () => void
}) {
  const { initial, schema, label, save, onDone } = props
  const queryClient = useQueryClient()
  const { register, handleSubmit, formState } = useForm<NameForm>({
    resolver: zodResolver(schema),
    defaultValues: { name: initial },
  })
  const mutation = useMutation({
    mutationFn: (values: NameForm) => save(values.name),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: classKeys.all })
      onDone()
    },
  })
  const error = formState.errors.name?.message ?? (mutation.isError ? 'Không lưu được, vui lòng thử lại.' : undefined)

  return (
    <form className="space-y-1 text-base font-normal" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))}>
      <div className="flex flex-wrap gap-2">
        <input
          aria-label={label}
          autoFocus
          className={`${input} min-w-0 flex-1 basis-48`}
          aria-invalid={error ? true : undefined}
          {...register('name')}
        />
        <div className="flex gap-2">
          <button type="submit" className={button('primary', 'sm')} disabled={mutation.isPending}>
            Lưu
          </button>
          <button type="button" className={button('secondary', 'sm')} onClick={onDone}>
            Huỷ
          </button>
        </div>
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
    </form>
  )
}

type StudentListInput = z.input<typeof studentListSchema>
type StudentListOutput = z.output<typeof studentListSchema>

function AddStudentsForm(props: { classId: number; remaining: number; onAdded: (codes: StudentCode[]) => void }) {
  const { classId, remaining, onAdded } = props
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, formState } = useForm<StudentListInput, unknown, StudentListOutput>({
    resolver: zodResolver(studentListSchema),
    defaultValues: { names: '' },
  })

  const add = useMutation({
    mutationFn: (values: StudentListOutput) => classApi.addStudents(classId, values.names),
    onSuccess: (codes) => {
      onAdded(codes)
      reset({ names: '' })
      return queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const error =
    formState.errors.names?.message ??
    (add.error
      ? problemOf(add.error)?.status === 409
        ? `Lớp chỉ còn chỗ cho ${remaining} học sinh.`
        : 'Không thêm được học sinh, vui lòng thử lại.'
      : undefined)

  return (
    <form className={`${card} space-y-3 self-start`} noValidate onSubmit={handleSubmit((v) => add.mutate(v))}>
      <div>
        <h2 className="text-heading text-ink">Thêm học sinh</h2>
        <p id="add-students-hint" className={muted}>
          Mỗi dòng một tên. Còn trống {remaining} chỗ.
        </p>
      </div>
      <textarea
        aria-label="Danh sách tên học sinh"
        aria-describedby={error ? 'add-students-error' : 'add-students-hint'}
        rows={8}
        placeholder={'Nguyễn Văn An\nTrần Thị Bình'}
        aria-invalid={error ? true : undefined}
        className={textarea}
        {...register('names')}
      />
      {error && (
        <p id="add-students-error" className="text-sm text-danger">
          {error}
        </p>
      )}
      <button type="submit" className={`${button('primary')} w-full`} disabled={add.isPending || remaining <= 0}>
        <Icon name="plus" />
        Thêm và tạo mã
      </button>
    </form>
  )
}
