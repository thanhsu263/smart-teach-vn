import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './auth-context'

/** Protected routes: wait for session restore, then send anonymous users to /login. */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageMessage text="Đang tải…" />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Login / register pages: signed-in users go straight to the app. */
export function GuestOnly() {
  const { user, loading } = useAuth()

  if (loading) return <FullPageMessage text="Đang tải…" />
  if (user) return <Navigate to="/" replace />
  return <Outlet />
}

function FullPageMessage({ text }: { text: string }) {
  return <div className="grid min-h-screen place-items-center text-slate-500">{text}</div>
}
