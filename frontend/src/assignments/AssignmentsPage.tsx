import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card, select } from '../components/ui'
import { assignmentApi, assignmentKeys, statusClass, statusLabel } from './api'
import { MAX_ASSIGNMENTS } from './schemas'

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

export function AssignmentsPage() {
  // The class filter lives in the URL so "Giao bài" on a class page can link straight to it.
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

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bài tập</h1>
          <p className="text-sm text-slate-500">
            Giao quiz cho lớp; học sinh làm bằng mã riêng. Tối đa {MAX_ASSIGNMENTS} bài tập.
          </p>
        </div>
        <div className="flex gap-2">
          <select
            aria-label="Lọc theo lớp"
            className={select}
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
          <Link
            to={classId ? `/assignments/new?classId=${classId}` : '/assignments/new'}
            className={button.primary}
          >
            Giao bài
          </Link>
        </div>
      </div>

      {assignments.isPending && <p className="text-slate-500">Đang tải…</p>}
      {assignments.isError && <FormError message="Không tải được danh sách bài tập." />}
      {assignments.data?.totalElements === 0 && (
        <div className={`${card} text-center text-slate-500`}>Chưa có bài tập nào. Bấm “Giao bài” để bắt đầu.</div>
      )}

      {assignments.data && assignments.data.content.length > 0 && (
        <ul className="space-y-3">
          {assignments.data.content.map((a) => (
            <li key={a.id}>
              <Link to={`/assignments/${a.id}`} className={`${card} flex flex-wrap items-center gap-3 hover:ring-blue-300`}>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-lg font-semibold text-slate-900">{a.title}</p>
                  <p className="text-sm text-slate-500">
                    Lớp {a.className}
                    {a.dueAt && ` · Hạn ${dateFormat.format(new Date(a.dueAt))}`}
                  </p>
                </div>
                {a.status !== 'draft' && <span className="font-mono text-sm text-slate-600">{a.publicCode}</span>}
                <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusClass[a.status]}`}>
                  {statusLabel[a.status]}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {assignments.data && assignments.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button type="button" className={button.secondary} disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Trước
          </button>
          <span className="text-slate-600">
            Trang {page + 1} / {assignments.data.totalPages}
          </span>
          <button
            type="button"
            className={button.secondary}
            disabled={page + 1 >= assignments.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
          </button>
        </div>
      )}
    </div>
  )
}
