import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { FormError } from '../components/form'
import { button, card } from '../components/ui'
import { problemOf } from '../lib/api'
import type { Analytics, Assignment } from '../lib/types'
import { assignmentApi, assignmentKeys, statusClass, statusLabel, studentLink } from './api'
import { REVEAL_LABELS } from './schemas'

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
const formatDate = (iso: string | null, empty: string) => (iso ? dateFormat.format(new Date(iso)) : empty)

export function AssignmentDetailPage() {
  const id = Number(useParams().id)
  const assignment = useQuery({ queryKey: assignmentKeys.detail(id), queryFn: () => assignmentApi.get(id) })

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

  const a = assignment.data
  return (
    <div className="space-y-6">
      <Link to="/assignments" className="text-sm text-blue-600 hover:underline">
        ← Bài tập
      </Link>
      <Header assignment={a} />
      {a.status === 'draft' ? (
        <div className={`${card} text-sm text-slate-600`}>
          Bản nháp chưa hiện với học sinh. Bấm “Giao bài” để chốt câu hỏi của quiz và lấy mã bài.
        </div>
      ) : (
        <>
          <ShareBox assignment={a} />
          <AnalyticsSection id={a.id} />
        </>
      )}
    </div>
  )
}

function Header({ assignment: a }: { assignment: Assignment }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: assignmentKeys.all })

  const publish = useMutation({ mutationFn: () => assignmentApi.publish(a.id), onSuccess: refresh })
  const close = useMutation({ mutationFn: () => assignmentApi.close(a.id), onSuccess: refresh })
  const remove = useMutation({
    mutationFn: () => assignmentApi.remove(a.id),
    onSuccess: async () => {
      await refresh()
      navigate('/assignments')
    },
  })
  const failed = publish.error ?? close.error ?? remove.error
  // 409 details explain the rule (no questions, due date passed…), so show them as they are.
  const error = failed ? (problemOf(failed)?.detail ?? 'Không thực hiện được, hãy thử lại.') : undefined

  return (
    <section className={`${card} space-y-4`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <div className="flex items-center gap-2">
            <h1 className="truncate text-2xl font-bold text-slate-900">{a.title}</h1>
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusClass[a.status]}`}>
              {statusLabel[a.status]}
            </span>
          </div>
          <p className="text-sm text-slate-500">
            {a.className}
            {a.questionCount !== null && ` · ${a.questionCount} câu hỏi`} · Mở {formatDate(a.opensAt, 'ngay')} · Hạn{' '}
            {formatDate(a.dueAt, 'không giới hạn')}
          </p>
          <p className="text-sm text-slate-500">Xem điểm: {REVEAL_LABELS[a.revealPolicy].toLowerCase()}</p>
          {a.description && <p className="whitespace-pre-line pt-1 text-slate-700">{a.description}</p>}
        </div>
        <div className="flex flex-wrap gap-2">
          {a.status === 'draft' && (
            <button
              type="button"
              className={button.primary}
              disabled={publish.isPending}
              onClick={() => publish.mutate()}
            >
              Giao bài
            </button>
          )}
          {a.status !== 'closed' && (
            <Link to={`/assignments/${a.id}/edit`} className={button.secondary}>
              Sửa
            </Link>
          )}
          {a.status === 'published' && (
            <button
              type="button"
              className={button.secondary}
              disabled={close.isPending}
              onClick={() => {
                if (window.confirm('Đóng bài? Học sinh sẽ không làm tiếp được nữa.')) close.mutate()
              }}
            >
              Đóng bài
            </button>
          )}
          <button
            type="button"
            className={button.danger}
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`Xoá bài "${a.title}" và toàn bộ bài làm của học sinh?`)) remove.mutate()
            }}
          >
            Xoá
          </button>
        </div>
      </div>
      <FormError message={error} />
    </section>
  )
}

function ShareBox({ assignment: a }: { assignment: Assignment }) {
  const [copied, setCopied] = useState(false)
  const link = studentLink(a.publicCode)

  const copy = async () => {
    await navigator.clipboard.writeText(link)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <section className={`${card} flex flex-wrap items-center gap-4`}>
      <div>
        <p className="text-sm text-slate-500">Mã bài</p>
        <p className="font-mono text-2xl font-bold tracking-widest text-slate-900">{a.publicCode}</p>
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm text-slate-500">Gửi link cho học sinh; mỗi em nhập mã học sinh riêng để làm bài.</p>
        <p className="truncate font-mono text-sm text-blue-700">{link}</p>
      </div>
      <button type="button" className={button.secondary} onClick={() => void copy()}>
        {copied ? 'Đã chép' : 'Chép link'}
      </button>
    </section>
  )
}

const progressLabel: Record<Analytics['students'][number]['status'], string> = {
  not_started: 'Chưa làm',
  in_progress: 'Đang làm',
  submitted: 'Đã nộp',
  invalidated: 'Bị huỷ',
}

function AnalyticsSection({ id }: { id: number }) {
  const analytics = useQuery({ queryKey: assignmentKeys.analytics(id), queryFn: () => assignmentApi.analytics(id) })

  if (analytics.isPending) return <p className="text-slate-500">Đang tải thống kê…</p>
  if (analytics.isError) return <FormError message="Không tải được thống kê." />
  const data = analytics.data

  return (
    <>
      <section className="grid gap-4 sm:grid-cols-3">
        <Stat label="Đã nộp" value={`${data.submittedCount} / ${data.studentCount}`} />
        <Stat label="Đang làm" value={String(data.inProgressCount)} />
        <Stat label="Điểm trung bình" value={data.averageScore === null ? '—' : `${data.averageScore} / 100`} />
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className={card}>
          <h2 className="mb-3 font-semibold text-slate-900">Tỉ lệ đúng từng câu</h2>
          <ol className="space-y-3">
            {data.questions.map((q) => (
              <li key={q.position} className="space-y-1 text-sm">
                <div className="flex justify-between gap-3">
                  <span className="truncate text-slate-700">
                    {q.position + 1}. {q.prompt}
                  </span>
                  <span className="shrink-0 text-slate-500">
                    {q.correctRate === null ? '—' : `${q.correctRate}%`}
                  </span>
                </div>
                <div className="h-2 rounded-full bg-slate-100">
                  <div className="h-2 rounded-full bg-blue-500" style={{ width: `${q.correctRate ?? 0}%` }} />
                </div>
              </li>
            ))}
          </ol>
        </section>

        <section className={card}>
          <h2 className="mb-3 font-semibold text-slate-900">Học sinh</h2>
          <table className="w-full text-sm">
            <thead className="text-left text-slate-500">
              <tr>
                <th className="py-2 font-medium">Họ tên</th>
                <th className="py-2 font-medium">Trạng thái</th>
                <th className="py-2 text-right font-medium">Điểm</th>
              </tr>
            </thead>
            <tbody>
              {data.students.map((s) => (
                <tr key={s.studentId} className="border-t border-slate-100">
                  <td className="py-2 text-slate-900">{s.name}</td>
                  <td className={`py-2 ${s.status === 'not_started' ? 'text-red-600' : 'text-slate-600'}`}>
                    {progressLabel[s.status]}
                  </td>
                  <td className="py-2 text-right text-slate-900">{s.score ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>
    </>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className={card}>
      <p className="text-sm text-slate-500">{label}</p>
      <p className="text-2xl font-bold text-slate-900">{value}</p>
    </div>
  )
}
