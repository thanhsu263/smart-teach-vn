import { Link, NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { button } from './ui'

export function AppLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-6 py-3">
          <div className="flex items-center gap-6">
            <Link to="/" className="font-bold text-blue-600">
              SmartTeach
            </Link>
            <nav className="text-sm">
              <NavLink
                to="/classes"
                className={({ isActive }) =>
                  isActive ? 'font-semibold text-slate-900' : 'text-slate-600 hover:text-slate-900'
                }
              >
                Lớp học
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-4 text-sm">
            <span className="text-slate-600">{user?.fullName}</span>
            <button type="button" onClick={() => void logout()} className={button.secondary}>
              Đăng xuất
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl p-6">
        <Outlet />
      </main>
    </div>
  )
}
