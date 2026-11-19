import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import type { z } from 'zod'
import { FormError } from '../components/form'
import { button, card, input } from '../components/ui'
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

  if (classroom.isPending) return <p className="text-slate-500">Đang tải…</p>
  if (classroom.isError) {
    const notFound = problemOf(classroom.error)?.status === 404
    return (
      <div className={`${card} space-y-3`}>
        <p className="text-slate-700">{notFound ? 'Không tìm thấy lớp này.' : 'Không tải được lớp.'}</p>
        <Link to="/classes" className="text-sm text-blue-600 hover:underline">
          ← Về danh sách lớp
        </Link>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <Link to="/classes" className="text-sm text-blue-600 hover:underline">
        ← Lớp học
      </Link>
      <ClassHeader classroom={classroom.data} />

      {newCodes.length > 0 && <NewCodesPanel codes={newCodes} onClose={() => setNewCodes([])} />}

      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <section className={card}>
          <h2 className="mb-3 font-semibold text-slate-900">Học sinh ({classroom.data.studentCount})</h2>
          {students.isPending && <p className="text-slate-500">Đang tải…</p>}
          {students.isError && <FormError message="Không tải được danh sách học sinh." />}
          {students.data?.length === 0 && <p className="text-sm text-slate-500">Lớp chưa có học sinh.</p>}
          {students.data && students.data.length > 0 && (
            <table className="w-full text-sm">
              <thead className="text-left text-slate-500">
                <tr>
                  <th className="w-10 py-2 font-medium">#</th>
                  <th className="py-2 font-medium">Họ tên</th>
                  <th className="py-2 font-medium">Mã</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {students.data.map((s, index) => (
                  <StudentRow
                    key={s.id}
                    index={index}
                    student={s}
                    classId={classId}
                    onNewCode={(code) => setNewCodes([code])}
                  />
                ))}
              </tbody>
            </table>
          )}
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

  const confirmDelete = () => {
    if (window.confirm(`Xoá lớp "${classroom.name}"? Học sinh và kết quả bài tập của lớp cũng bị xoá.`)) {
      remove.mutate()
    }
  }

  if (editing) {
    return (
      <NameEditor
        initial={classroom.name}
        schema={classNameSchema}
        label="Tên lớp"
        save={(name) => classApi.rename(classroom.id, name)}
        onDone={() => setEditing(false)}
      />
    )
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <h1 className="text-2xl font-bold text-slate-900">{classroom.name}</h1>
      <div className="flex gap-2">
        <button type="button" className={button.secondary} onClick={() => setEditing(true)}>
          Đổi tên
        </button>
        <button type="button" className={button.danger} onClick={confirmDelete} disabled={remove.isPending}>
          Xoá lớp
        </button>
      </div>
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
  const refresh = () => queryClient.invalidateQueries({ queryKey: classKeys.all })

  const regenerate = useMutation({
    mutationFn: () => classApi.regenerateCode(student.id),
    onSuccess: (code) => {
      onNewCode(code)
      return queryClient.invalidateQueries({ queryKey: classKeys.students(classId) })
    },
  })
  const remove = useMutation({ mutationFn: () => classApi.removeStudent(student.id), onSuccess: refresh })

  if (editing) {
    return (
      <tr className="border-t border-slate-100">
        <td className="py-2 text-slate-500">{index + 1}</td>
        <td colSpan={3} className="py-2">
          <NameEditor
            initial={student.name}
            schema={studentNameSchema}
            label="Họ tên học sinh"
            save={(name) => classApi.renameStudent(student.id, name)}
            onDone={() => setEditing(false)}
          />
        </td>
      </tr>
    )
  }

  return (
    <tr className="border-t border-slate-100">
      <td className="py-2 text-slate-500">{index + 1}</td>
      <td className="py-2 text-slate-900">{student.name}</td>
      <td className="py-2 font-mono text-slate-500" title="4 ký tự cuối của mã">
        ••••{student.codeHint}
      </td>
      <td className="py-2">
        <div className="flex justify-end gap-1">
          <button type="button" className={button.secondary} onClick={() => setEditing(true)}>
            Sửa
          </button>
          <button
            type="button"
            className={button.secondary}
            disabled={regenerate.isPending}
            onClick={() => {
              if (window.confirm(`Cấp mã mới cho ${student.name}? Mã cũ sẽ không dùng được nữa.`)) regenerate.mutate()
            }}
          >
            Cấp lại mã
          </button>
          <button
            type="button"
            className={button.danger}
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`Xoá học sinh ${student.name} và kết quả bài làm?`)) remove.mutate()
            }}
          >
            Xoá
          </button>
        </div>
      </td>
    </tr>
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
    <form className="space-y-1" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))}>
      <div className="flex gap-2">
        <input aria-label={label} autoFocus className={input} aria-invalid={error ? true : undefined} {...register('name')} />
        <button type="submit" className={`${button.primary} shrink-0`} disabled={mutation.isPending}>
          Lưu
        </button>
        <button type="button" className={`${button.secondary} shrink-0`} onClick={onDone}>
          Huỷ
        </button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
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
        <h2 className="font-semibold text-slate-900">Thêm học sinh</h2>
        <p className="text-sm text-slate-500">Mỗi dòng một tên. Còn trống {remaining} chỗ.</p>
      </div>
      <textarea
        aria-label="Danh sách tên học sinh"
        rows={8}
        placeholder={'Nguyễn Văn An\nTrần Thị Bình'}
        aria-invalid={error ? true : undefined}
        className={input}
        {...register('names')}
      />
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button type="submit" className={`${button.primary} w-full`} disabled={add.isPending || remaining <= 0}>
        Thêm và tạo mã
      </button>
    </form>
  )
}
