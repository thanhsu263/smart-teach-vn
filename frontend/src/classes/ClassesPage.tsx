import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { FormError } from '../components/form'
import { button, card, input } from '../components/ui'
import { problemOf } from '../lib/api'
import { classApi, classKeys } from './api'
import { classNameSchema, type NameForm } from './schemas'

export function ClassesPage() {
  const [page, setPage] = useState(0)
  const classes = useQuery({
    queryKey: classKeys.list(page),
    queryFn: () => classApi.list(page),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Lớp học</h1>
          <p className="text-sm text-slate-500">Tối đa 20 lớp, mỗi lớp tối đa 100 học sinh.</p>
        </div>
        <CreateClassForm onCreated={() => setPage(0)} />
      </div>

      {classes.isPending && <p className="text-slate-500">Đang tải…</p>}
      {classes.isError && <FormError message="Không tải được danh sách lớp." />}
      {classes.data?.totalElements === 0 && (
        <div className={`${card} text-center text-slate-500`}>Chưa có lớp nào. Tạo lớp đầu tiên ở trên.</div>
      )}

      {classes.data && classes.data.content.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {classes.data.content.map((c) => (
            <li key={c.id}>
              <Link
                to={`/classes/${c.id}`}
                className={`${card} block transition hover:ring-2 hover:ring-blue-300`}
              >
                <p className="truncate text-lg font-semibold text-slate-900">{c.name}</p>
                <p className="mt-1 text-sm text-slate-500">{c.studentCount} học sinh</p>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {classes.data && classes.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            className={button.secondary}
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Trước
          </button>
          <span className="text-slate-600">
            Trang {page + 1} / {classes.data.totalPages}
          </span>
          <button
            type="button"
            className={button.secondary}
            disabled={page + 1 >= classes.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
          </button>
        </div>
      )}
    </div>
  )
}

function CreateClassForm({ onCreated }: { onCreated: () => void }) {
  const queryClient = useQueryClient()
  const { register, handleSubmit, reset, formState } = useForm<NameForm>({
    resolver: zodResolver(classNameSchema),
  })

  const create = useMutation({
    mutationFn: (values: NameForm) => classApi.create(values.name),
    onSuccess: () => {
      reset({ name: '' })
      onCreated()
      return queryClient.invalidateQueries({ queryKey: classKeys.all })
    },
  })

  const error =
    formState.errors.name?.message ??
    (create.error
      ? problemOf(create.error)?.status === 409
        ? 'Bạn đã có 20 lớp, hãy xoá bớt lớp cũ.'
        : 'Không tạo được lớp, vui lòng thử lại.'
      : undefined)

  return (
    <form className="w-full max-w-sm space-y-2" noValidate onSubmit={handleSubmit((v) => create.mutate(v))}>
      <div className="flex gap-2">
        <input
          aria-label="Tên lớp mới"
          placeholder="Tên lớp mới, ví dụ 10A1"
          aria-invalid={error ? true : undefined}
          className={input}
          {...register('name')}
        />
        <button type="submit" className={`${button.primary} shrink-0`} disabled={create.isPending}>
          Tạo lớp
        </button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </form>
  )
}
