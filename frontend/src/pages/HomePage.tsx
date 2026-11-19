import { useQuery } from '@tanstack/react-query'
import { useAuth } from '../auth/auth-context'
import { api } from '../lib/api'
import type { User } from '../lib/types'

export function HomePage() {
  const { logout } = useAuth()
  // Goes through the axios interceptor: an expired access token is refreshed transparently.
  const me = useQuery({
    queryKey: ['me'],
    queryFn: async () => (await api.get<User>('/api/me')).data,
  })

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
        <span className="font-bold text-blue-600">SmartTeach</span>
        <div className="flex items-center gap-4 text-sm">
          <span className="text-slate-600">{me.data?.fullName}</span>
          <button
            type="button"
            onClick={() => void logout()}
            className="rounded-lg border border-slate-300 px-3 py-1.5 hover:bg-slate-100"
          >
            Đăng xuất
          </button>
        </div>
      </header>
      <main className="mx-auto max-w-3xl p-6">
        {me.isPending && <p className="text-slate-500">Đang tải…</p>}
        {me.isError && <p className="text-red-600">Không tải được thông tin tài khoản.</p>}
        {me.data && (
          <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
            <h1 className="text-xl font-bold text-slate-900">Xin chào, {me.data.fullName}</h1>
            <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-slate-500">Email</dt>
              <dd>{me.data.email}</dd>
              <dt className="text-slate-500">Vai trò</dt>
              <dd>{me.data.role === 'ADMIN' ? 'Quản trị viên' : 'Giáo viên'}</dd>
            </dl>
            <p className="mt-6 text-sm text-slate-500">Quản lý lớp, quiz và bài tập sẽ có ở các phase tiếp theo.</p>
          </section>
        )}
      </main>
    </div>
  )
}
