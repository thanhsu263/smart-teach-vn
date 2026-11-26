import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { Icon } from '../components/Icon'
import { PageHeader, Pagination } from '../components/page'
import { EmptyState, QueryState } from '../components/states'
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
      <PageHeader
        title="Lớp học"
        description="Tối đa 20 lớp, mỗi lớp tối đa 100 học sinh."
        actions={<CreateClassForm onCreated={() => setPage(0)} />}
      />

      <QueryState
        query={classes}
        error="Không tải được danh sách lớp."
        isEmpty={(data) => data.totalElements === 0}
        empty={
          <EmptyState
            icon="classes"
            title="Chưa có lớp nào"
            description="Nhập tên lớp ở ô phía trên, ví dụ 10A1, rồi bấm “Tạo lớp”. Sau đó thêm học sinh để cấp mã làm bài."
          />
        }
      >
        {(data) => (
          <>
            <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {data.content.map((c) => (
                <li key={c.id}>
                  <Link
                    to={`/classes/${c.id}`}
                    className={`${card} block transition-shadow hover:border-primary hover:shadow-raised`}
                  >
                    <p className="truncate text-heading text-ink">{c.name}</p>
                    <p className="mt-2 flex items-center gap-2 text-sm text-ink-muted">
                      <Icon name="users" className="size-4" />
                      {c.studentCount} học sinh
                    </p>
                  </Link>
                </li>
              ))}
            </ul>
            <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
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
    <form className="w-full space-y-1.5 sm:w-96" noValidate onSubmit={handleSubmit((v) => create.mutate(v))}>
      <div className="flex gap-2">
        <input
          aria-label="Tên lớp mới"
          placeholder="Tên lớp mới, ví dụ 10A1"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? 'new-class-error' : undefined}
          className={input}
          {...register('name')}
        />
        <button type="submit" className={button('primary')} disabled={create.isPending}>
          <Icon name="plus" />
          Tạo lớp
        </button>
      </div>
      {error && (
        <p id="new-class-error" className="text-sm text-danger">
          {error}
        </p>
      )}
    </form>
  )
}
