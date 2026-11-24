import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { FormError } from '../components/form'
import { card } from '../components/ui'
import { adminApi, adminKeys } from './api'

export function AdminPage() {
  const { user } = useAuth()
  // The backend checks the role too; this only keeps teachers off a page full of 403s.
  if (user?.role !== 'ADMIN') return <Navigate to="/" replace />
  return <KeepAliveCard />
}

function KeepAliveCard() {
  const queryClient = useQueryClient()
  const status = useQuery({ queryKey: adminKeys.keepAlive, queryFn: adminApi.keepAlive })
  const toggle = useMutation({
    mutationFn: adminApi.setKeepAlive,
    onSuccess: (data) => queryClient.setQueryData(adminKeys.keepAlive, data),
  })

  const data = status.data
  const enabled = data?.enabled ?? false

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-900">Quản trị</h1>

      {status.isPending && <p className="text-slate-500">Đang tải…</p>}
      {status.isError && <FormError message="Không tải được trạng thái." />}

      {data && (
        <section className={`${card} max-w-xl space-y-4`}>
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="font-semibold text-slate-900">Giữ server luôn thức</h2>
              <p className="mt-1 text-sm text-slate-500">
                Gói miễn phí tắt server sau 15 phút không có truy cập, lần mở sau phải chờ 2–3 phút. Khi bật,
                server tự gọi chính nó mỗi {data.intervalMinutes} phút để không bị tắt.
              </p>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={enabled}
              aria-label="Giữ server luôn thức"
              disabled={toggle.isPending}
              onClick={() => toggle.mutate(!enabled)}
              className={`relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition disabled:opacity-60 ${
                enabled ? 'bg-blue-600' : 'bg-slate-300'
              }`}
            >
              <span
                className={`inline-block h-5 w-5 rounded-full bg-white shadow transition ${
                  enabled ? 'translate-x-6' : 'translate-x-1'
                }`}
              />
            </button>
          </div>

          {toggle.isError && <FormError message="Không đổi được trạng thái. Thử lại." />}

          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-slate-500">Trạng thái</dt>
            <dd className="font-medium text-slate-900">{enabled ? 'Đang bật' : 'Đang tắt'}</dd>
            <dt className="text-slate-500">Địa chỉ ping</dt>
            <dd className="break-all text-slate-900">
              {data.targetUrl ?? 'Chưa cấu hình (chạy local), bật cũng không ping'}
            </dd>
            <dt className="text-slate-500">Lần ping gần nhất</dt>
            <dd className="text-slate-900">
              {data.lastPingAt ? (
                <>
                  {new Date(data.lastPingAt).toLocaleString('vi-VN')} ·{' '}
                  <span className={data.lastPingOk ? 'text-green-700' : 'text-red-700'}>
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
    </div>
  )
}
