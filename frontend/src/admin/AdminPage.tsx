import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { FormError, Switch } from '../components/form'
import { PageHeader } from '../components/page'
import { QueryState, Skeleton } from '../components/states'
import { badge, card, muted } from '../components/ui'
import { adminApi, adminKeys } from './api'

export function AdminPage() {
  const { user } = useAuth()
  // The backend checks the role too; this only keeps teachers off a page full of 403s.
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return (
    <div className="space-y-6">
      <PageHeader title="Quản trị" description="Cài đặt chung của server." />
      <KeepAliveCard />
    </div>
  )
}

function KeepAliveCard() {
  const queryClient = useQueryClient()
  const status = useQuery({ queryKey: adminKeys.keepAlive, queryFn: adminApi.keepAlive })
  const toggle = useMutation({
    mutationFn: adminApi.setKeepAlive,
    onSuccess: (data) => queryClient.setQueryData(adminKeys.keepAlive, data),
  })

  return (
    <QueryState
      query={status}
      error="Không tải được trạng thái."
      loading={
        <div role="status" aria-label="Đang tải" className={`${card} max-w-xl space-y-3`}>
          <Skeleton className="h-5 w-1/2" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-4/5" />
        </div>
      }
    >
      {(data) => (
        <section className={`${card} max-w-xl space-y-4`}>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="text-heading text-ink">Giữ server luôn thức</h2>
              <p className={`mt-1 ${muted}`}>
                Gói miễn phí tắt server sau 15 phút không có truy cập, lần mở sau phải chờ 2–3 phút. Khi bật,
                server tự gọi chính nó mỗi {data.intervalMinutes} phút để không bị tắt.
              </p>
            </div>
            <Switch
              checked={data.enabled}
              label="Giữ server luôn thức"
              disabled={toggle.isPending}
              onChange={(enabled) => toggle.mutate(enabled)}
            />
          </div>

          {toggle.isError && <FormError message="Không đổi được trạng thái. Thử lại." />}

          <dl className="grid gap-x-4 gap-y-1 border-t border-border pt-4 text-sm sm:grid-cols-[auto_1fr] sm:gap-y-2">
            <dt className="text-ink-muted">Trạng thái</dt>
            <dd className="mb-2 sm:mb-0">
              <span className={badge(data.enabled ? 'success' : 'neutral')}>{data.enabled ? 'Đang bật' : 'Đang tắt'}</span>
            </dd>
            <dt className="text-ink-muted">Địa chỉ ping</dt>
            <dd className="mb-2 break-all text-ink sm:mb-0">
              {data.targetUrl ?? 'Chưa cấu hình (chạy local), bật cũng không ping'}
            </dd>
            <dt className="text-ink-muted">Lần ping gần nhất</dt>
            <dd className="text-ink">
              {data.lastPingAt ? (
                <>
                  {new Date(data.lastPingAt).toLocaleString('vi-VN')} ·{' '}
                  <span className={`font-semibold ${data.lastPingOk ? 'text-success' : 'text-danger'}`}>
                    {data.lastPingOk ? 'thành công' : 'thất bại'}
                  </span>
                </>
              ) : (
                'Chưa có (từ lúc server khởi động)'
              )}
            </dd>
          </dl>
        </section>
      )}
    </QueryState>
  )
}
