import { Navigate, Outlet, useLocation } from 'react-router'
import { FullPageLoading } from '../components/states'
import { useAuth } from './auth-context'

/** Protected routes: wait for session restore, then send anonymous users to /login. */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageLoading />
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}

/** Login / register pages: signed-in users go straight to the app. */
export function GuestOnly() {
  const { user, loading } = useAuth()

  if (loading) return <FullPageLoading />
  if (user) return <Navigate to="/" replace />
  return <Outlet />
}

