import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useConfirm } from '../components/confirm-context'
import { FormError } from '../components/form'
import { Icon } from '../components/Icon'
import { PageHeader } from '../components/page'
import { EmptyState, ErrorState, QueryState, Skeleton } from '../components/states'
import { badge, button, card, muted, panel } from '../components/ui'
import { problemOf } from '../lib/api'
import type { Analytics, Assignment, QuestionStats } from '../lib/types'
import { assignmentApi, assignmentKeys, studentLink } from './api'
import { REVEAL_LABELS } from './schemas'
import { StatusBadge } from './StatusBadge'

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })
const formatDate = (iso: string | null, empty: string) => (iso ? dateFormat.format(new Date(iso)) : empty)

export function AssignmentDetailPage() {
  const id = Number(useParams().id)
  const assignment = useQuery({ queryKey: assignmentKeys.detail(id), queryFn: () => assignmentApi.get(id) })

  if (assignment.isPending) {
    return (
      <div role="status" aria-label="Đang tải" className="space-y-6">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-9 w-64" />
        <Skeleton className="h-28 w-full" />
        <div className="grid gap-4 sm:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
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

  const a = assignment.data
  return (
    <div className="space-y-6">
      <Header assignment={a} />
      {a.status === 'draft' ? (
        <p className="rounded-md border border-dashed border-border bg-surface px-5 py-4 text-ink-muted">
          Bản nháp chưa hiện với học sinh. Bấm “Giao bài” để chốt câu hỏi của quiz và lấy mã bài.
        </p>
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
  const confirm = useConfirm()
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

  const confirmClose = async () => {
    const ok = await confirm({
      title: 'Đóng bài?',
      message: 'Học sinh sẽ không làm tiếp được nữa.',
      confirmLabel: 'Đóng bài',
    })
    if (ok) close.mutate()
  }

  const confirmRemove = async () => {
    const ok = await confirm({
      title: `Xoá bài "${a.title}"?`,
      message: 'Toàn bộ bài làm của học sinh cũng bị xoá. Không thể hoàn tác.',
      confirmLabel: 'Xoá bài',
      danger: true,
    })
    if (ok) remove.mutate()
  }

  return (
    <div className="space-y-3">
      <PageHeader
        back={{ to: '/assignments', label: 'Bài tập' }}
        title={a.title}
        description={
          <div className="space-y-1">
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <StatusBadge status={a.status} />
              <span>
                {a.className}
                {a.questionCount !== null && ` · ${a.questionCount} câu hỏi`}
              </span>
            </p>
            <p className="text-sm">
              Mở {formatDate(a.opensAt, 'ngay')} · Hạn {formatDate(a.dueAt, 'không giới hạn')} · Xem điểm:{' '}
              {REVEAL_LABELS[a.revealPolicy].toLowerCase()}
            </p>
          </div>
        }
        actions={
          <>
            {a.status === 'draft' && (
              <button
                type="button"
                className={button('primary', 'sm')}
                disabled={publish.isPending}
                onClick={() => publish.mutate()}
              >
                Giao bài
              </button>
            )}
            {a.status !== 'closed' && (
              <Link to={`/assignments/${a.id}/edit`} className={button('secondary', 'sm')}>
                Sửa
              </Link>
            )}
            {a.status === 'published' && (
              <button
                type="button"
                className={button('secondary', 'sm')}
                disabled={close.isPending}
                onClick={() => void confirmClose()}
              >
                Đóng bài
              </button>
            )}
            <button
              type="button"
              className={button('ghostDanger', 'sm')}
              disabled={remove.isPending}
              onClick={() => void confirmRemove()}
            >
              Xoá
            </button>
          </>
        }
      />
      {a.description && <p className={`${panel} px-5 py-4 whitespace-pre-line text-ink`}>{a.description}</p>}
      <FormError message={error} />
    </div>
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
    <section className="flex flex-wrap items-center gap-x-6 gap-y-4 rounded-md border border-primary/30 bg-primary-tint p-5">
      <div>
        <p className={muted}>Mã bài</p>
        <p className="font-mono text-2xl font-bold tracking-widest text-brand">{a.publicCode}</p>
      </div>
      <div className="min-w-0 flex-1 basis-56">
        <p className={muted}>Gửi link cho học sinh; mỗi em nhập mã học sinh riêng để làm bài.</p>
        <p className="truncate font-mono text-sm text-primary-deep">{link}</p>
      </div>
      <button type="button" className={button('secondary', 'sm')} onClick={() => void copy()}>
        <Icon name={copied ? 'check' : 'copy'} className="size-4" />
        {copied ? 'Đã chép' : 'Chép link'}
      </button>
    </section>
  )
}

type Progress = Analytics['students'][number]['status']

const progress: Record<Progress, { label: string; tone: Parameters<typeof badge>[0] }> = {
  not_started: { label: 'Chưa làm', tone: 'danger' },
  in_progress: { label: 'Đang làm', tone: 'info' },
  submitted: { label: 'Đã nộp', tone: 'success' },
  invalidated: { label: 'Bị huỷ', tone: 'neutral' },
}

function AnalyticsSection({ id }: { id: number }) {
  const analytics = useQuery({ queryKey: assignmentKeys.analytics(id), queryFn: () => assignmentApi.analytics(id) })

  return (
    <QueryState
      query={analytics}
      error="Không tải được thống kê."
      loading={
        <div role="status" aria-label="Đang tải thống kê" className="grid gap-4 sm:grid-cols-3">
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
          <Skeleton className="h-24" />
        </div>
      }
    >
      {(data) => (
        <>
          <section className="grid gap-4 sm:grid-cols-3" aria-label="Tổng quan">
            <Stat label="Đã nộp" value={`${data.submittedCount} / ${data.studentCount}`} />
            <Stat label="Đang làm" value={String(data.inProgressCount)} />
            <Stat label="Điểm trung bình" value={data.averageScore === null ? '—' : `${data.averageScore} / 100`} />
          </section>

          {/* Item analysis comes before the student list, which can be 40+ rows long. */}
          <QuestionStatsSection id={id} />

          <section className={panel}>
            <h2 className="px-5 pt-5 pb-3 text-heading text-ink">Học sinh</h2>
            {data.students.length === 0 ? (
              <p className={`${muted} px-5 pb-5`}>Lớp chưa có học sinh.</p>
            ) : (
              <table className="w-full text-sm">
                <thead className="text-left text-ink-muted">
                  <tr>
                    <th className="px-5 py-2 font-semibold">Họ tên</th>
                    <th className="px-2 py-2 font-semibold">Trạng thái</th>
                    <th className="px-5 py-2 text-right font-semibold">Điểm</th>
                  </tr>
                </thead>
                <tbody>
                  {data.students.map((s) => (
                    <tr key={s.studentId} className="border-t border-border">
                      <td className="px-5 py-2.5 text-ink">{s.name}</td>
                      <td className="px-2 py-2.5">
                        <span className={badge(progress[s.status].tone)}>{progress[s.status].label}</span>
                      </td>
                      <td className="px-5 py-2.5 text-right font-semibold text-ink">{s.score ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </section>
        </>
      )}
    </QueryState>
  )
}

type QuestionStat = QuestionStats['questions'][number]

/** Hardest first; without submissions there is no rate and the quiz order is kept. */
const byDifficulty = (a: QuestionStat, b: QuestionStat) =>
  (a.correctRate ?? 0) - (b.correctRate ?? 0) || a.position - b.position

function QuestionStatsSection({ id }: { id: number }) {
  const stats = useQuery({ queryKey: assignmentKeys.questionStats(id), queryFn: () => assignmentApi.questionStats(id) })

  return (
    <section className={panel} aria-labelledby="question-stats-title">
      <div className="space-y-1 px-5 pt-5 pb-3">
        <h2 id="question-stats-title" className="text-heading text-ink">
          Phân tích câu hỏi
        </h2>
        <p className={muted}>
          Xếp từ câu khó nhất. Độ phân biệt = tỉ lệ đúng của 27% bài điểm cao nhất trừ 27% bài điểm thấp nhất (từ −1
          đến 1); dưới 0.2 là câu chưa tách được học sinh giỏi với học sinh yếu.
        </p>
      </div>
      <QueryState
        query={stats}
        error="Không tải được phân tích câu hỏi."
        loading={
          <div role="status" aria-label="Đang tải phân tích câu hỏi" className="space-y-2 px-5 pb-5">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        }
      >
        {(data) =>
          data.submittedCount === 0 ? (
            <p className={`${muted} px-5 pb-5`}>Chưa có bài nộp nào.</p>
          ) : (
            <>
              {data.submittedCount < data.minSubmissions && (
                <p className="mx-5 mb-3 rounded-sm bg-page px-3 py-2 text-sm text-ink-muted">
                  Cần ít nhất {data.minSubmissions} bài nộp để tính độ phân biệt (hiện có {data.submittedCount}).
                </p>
              )}
              <div className="overflow-x-auto">
                <table className="w-full min-w-[40rem] text-sm">
                  <thead className="text-left text-ink-muted">
                    <tr>
                      <th className="px-5 py-2 font-semibold">Câu</th>
                      <th className="px-2 py-2 text-right font-semibold">Tỉ lệ đúng</th>
                      <th className="px-2 py-2 text-right font-semibold">Độ phân biệt</th>
                      <th className="px-2 py-2 font-semibold">Sai nhiều nhất</th>
                      <th className="px-5 py-2 font-semibold">Nhận xét</th>
                    </tr>
                  </thead>
                  <tbody>
                    {[...data.questions].sort(byDifficulty).map((q) => (
                      <tr key={q.position} className="border-t border-border align-top">
                        <td className="max-w-xs px-5 py-2.5 text-ink">
                          <span className="font-semibold">Câu {q.position + 1}.</span> {q.prompt}
                        </td>
                        <td className="px-2 py-2.5 text-right font-semibold whitespace-nowrap text-ink">
                          {q.correctRate === null ? '—' : `${q.correctRate}%`}
                        </td>
                        <td className="px-2 py-2.5 text-right whitespace-nowrap">
                          {q.discrimination === null ? (
                            <span className="text-ink-muted">chưa đủ dữ liệu</span>
                          ) : (
                            <span className="font-semibold text-ink">{q.discrimination.toFixed(2)}</span>
                          )}
                        </td>
                        <td className="px-2 py-2.5 text-ink">
                          {q.mostCommonWrongAnswer ? (
                            <>
                              {q.mostCommonWrongAnswer.answer}{' '}
                              <span className="whitespace-nowrap text-ink-muted">
                                ({q.mostCommonWrongAnswer.count} bài)
                              </span>
                            </>
                          ) : (
                            <span className="text-ink-muted">—</span>
                          )}
                        </td>
                        <td className="px-5 py-2.5">
                          <div className="flex flex-wrap gap-1.5">
                            {q.tooHard && <span className={badge('danger')}>Quá khó</span>}
                            {q.lowDiscrimination && <span className={badge('warning')}>Phân biệt kém</span>}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </>
          )
        }
      </QueryState>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className={card}>
      <p className={muted}>{label}</p>
      <p className="mt-1 text-2xl font-bold text-ink">{value}</p>
    </div>
  )
}
