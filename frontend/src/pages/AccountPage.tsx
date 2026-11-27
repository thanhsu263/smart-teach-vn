import { useMutation } from '@tanstack/react-query'
import { useAuth } from '../auth/auth-context'
import { FormError, Switch } from '../components/form'
import { PageHeader } from '../components/page'
import { card, muted } from '../components/ui'
import { api } from '../lib/api'
import type { User } from '../lib/types'

export function AccountPage() {
  const { user, updateUser } = useAuth()
  const save = useMutation({
    mutationFn: async (emailNotifications: boolean) =>
      (await api.put<User>('/api/me/settings', { emailNotifications })).data,
    onSuccess: updateUser,
  })

  // RequireAuth renders this page only with a user.
  if (!user) return null
  const enabled = save.isPending ? save.variables : user.emailNotifications

  return (
    <div className="space-y-6">
      <PageHeader title="Tài khoản" />

      <section className={`${card} max-w-xl`}>
        <h2 className="text-heading text-ink">Thông tin</h2>
        <dl className="mt-3 grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[auto_1fr] sm:gap-y-2">
          <dt className="text-ink-muted">Họ tên</dt>
          <dd className="mb-2 text-ink sm:mb-0">{user.fullName}</dd>
          <dt className="text-ink-muted">Email</dt>
          <dd className="break-all text-ink">{user.email}</dd>
        </dl>
      </section>

      <section className={`${card} max-w-xl space-y-4`}>
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-heading text-ink">Email về bài tập</h2>
            <p className={`mt-1 ${muted}`}>
              Nhắc bạn 24 giờ trước hạn nộp nếu còn học sinh chưa nộp, và gửi kết quả (số bài đã nộp, điểm trung
              bình, câu sai nhiều nhất) khi bài tập kết thúc. Email đặt lại mật khẩu vẫn luôn được gửi.
            </p>
          </div>
          <Switch
            checked={enabled}
            label="Nhận email về bài tập"
            disabled={save.isPending}
            onChange={(checked) => save.mutate(checked)}
          />
        </div>
        {save.isError && <FormError message="Không lưu được cài đặt. Thử lại." />}
        <p role="status" className={muted}>
          {save.isSuccess && (enabled ? 'Đã bật email.' : 'Đã tắt email.')}
        </p>
      </section>
    </div>
  )
}
