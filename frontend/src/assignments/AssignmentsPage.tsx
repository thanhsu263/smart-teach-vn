import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { Icon } from '../components/Icon'
import { PageHeader, Pagination } from '../components/page'
import { EmptyState, QueryState } from '../components/states'
import { button, card, muted, select } from '../components/ui'
import { assignmentApi, assignmentKeys } from './api'
import { MAX_ASSIGNMENTS } from './schemas'
import { StatusBadge } from './StatusBadge'

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

export function AssignmentsPage() {
  // The class filter lives in the URL so "Bài tập của lớp" on a class page can link straight to it.
  const [params, setParams] = useSearchParams()
  const classId = params.get('classId') ? Number(params.get('classId')) : null
  const [page, setPage] = useState(0)

  const classes = useQuery({ queryKey: ['classes', 'all'], queryFn: assignmentApi.allClasses })
  const assignments = useQuery({
    queryKey: assignmentKeys.list(classId, page),
    queryFn: () => assignmentApi.list(classId, page),
    placeholderData: keepPreviousData,
  })

  const changeClass = (value: string) => {
    setPage(0)
    setParams(value ? { classId: value } : {})
  }

  const newAssignment = (
    <Link to={classId ? `/assignments/new?classId=${classId}` : '/assignments/new'} className={button('primary')}>
      <Icon name="plus" />
      Giao bài
    </Link>
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Bài tập"
        description={`Giao quiz cho lớp; học sinh làm bằng mã riêng. Tối đa ${MAX_ASSIGNMENTS} bài tập.`}
        actions={
          <>
            <select
              aria-label="Lọc theo lớp"
              className={`${select} max-sm:flex-1`}
              value={classId ?? ''}
              onChange={(e) => changeClass(e.target.value)}
            >
              <option value="">Tất cả lớp</option>
              {classes.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            {newAssignment}
          </>
        }
      />

      <QueryState
        query={assignments}
        error="Không tải được danh sách bài tập."
        isEmpty={(data) => data.totalElements === 0}
        empty={
          <EmptyState
            icon="assignment"
            title={classId ? 'Lớp này chưa có bài tập' : 'Chưa có bài tập nào'}
            description="Chọn lớp và quiz để tạo bản nháp, rồi bấm “Giao bài” để lấy mã gửi cho học sinh."
            action={newAssignment}
          />
        }
      >
        {(data) => (
          <>
            <ul className="space-y-3">
              {data.content.map((a) => (
                <li key={a.id}>
                  <Link
                    to={`/assignments/${a.id}`}
                    className={`${card} group flex flex-wrap items-center gap-x-4 gap-y-2 transition-shadow hover:border-primary hover:shadow-raised`}
                  >
                    <div className="min-w-0 flex-1 basis-48">
                      <p className="truncate text-heading text-ink group-hover:text-primary">{a.title}</p>
                      <p className={muted}>
                        {a.className}
                        {a.dueAt && ` · Hạn ${dateFormat.format(new Date(a.dueAt))}`}
                      </p>
                    </div>
                    {a.status !== 'draft' && (
                      <span className="font-mono text-sm text-ink-muted" title="Mã bài">
                        {a.publicCode}
                      </span>
                    )}
                    <StatusBadge status={a.status} />
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
