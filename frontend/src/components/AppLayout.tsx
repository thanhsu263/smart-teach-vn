import { useEffect, useRef } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/auth-context'
import { Logo } from './form'
import { Icon, type IconName } from './Icon'
import { button } from './ui'

const links: { to: string; label: string; icon: IconName; adminOnly?: boolean }[] = [
  { to: '/classes', label: 'Lớp học', icon: 'classes' },
  { to: '/quizzes', label: 'Quiz', icon: 'quiz' },
  { to: '/assignments', label: 'Bài tập', icon: 'assignment' },
  { to: '/admin', label: 'Quản trị', icon: 'admin', adminOnly: true },
]

/**
 * Teacher app shell from Figma: sidebar of 248px from 960px wide, 208px from 720px, and below that a
 * drawer opened from the top bar. Admin accounts get a navy sidebar, so it is clear which account is in use.
 */
export function AppLayout() {
  const { user, logout } = useAuth()
  const admin = user?.role === 'ADMIN'
  const drawerRef = useRef<HTMLDialogElement>(null)
  const { pathname } = useLocation()

  // Picking a page in the drawer navigates; close the drawer when that happens.
  useEffect(() => drawerRef.current?.close(), [pathname])

  const nav = <Nav admin={admin} />

  return (
    <div className="min-h-screen md:grid md:grid-cols-[13rem_1fr] lg:grid-cols-[15.5rem_1fr]">
      <a
        href="#main"
        className={`${button('primary', 'sm')} fixed top-2 left-2 z-50 -translate-y-20 focus:translate-y-0`}
      >
        Bỏ qua tới nội dung
      </a>

      {/* The outer box stretches to the page's full height (colour and border); the inner one stays in view. */}
      <aside className={`hidden border-r md:block ${admin ? 'border-ink bg-ink' : 'border-border bg-surface'}`}>
        <div className="sticky top-0 flex h-screen flex-col">
          <div className="flex h-18 shrink-0 items-center px-5">
            <Logo inverse={admin} />
          </div>
          {nav}
        </div>
      </aside>

      <dialog
        ref={drawerRef}
        aria-label="Menu"
        // A click on the backdrop lands on the <dialog> element itself.
        onClick={(e) => e.target === e.currentTarget && e.currentTarget.close()}
        className={`m-0 h-full max-h-none w-76 max-w-[85vw] shadow-raised md:hidden ${admin ? 'bg-ink' : 'bg-surface'}`}
      >
        <div className="flex h-16 items-center justify-between px-4">
          <Logo inverse={admin} />
          <button
            type="button"
            aria-label="Đóng menu"
            onClick={() => drawerRef.current?.close()}
            className={`grid size-11 place-items-center rounded-sm ${
              admin ? 'text-white hover:bg-white/10' : 'text-ink hover:bg-primary-tint'
            }`}
          >
            <Icon name="close" />
          </button>
        </div>
        {nav}
      </dialog>

      <div className="min-w-0">
        <header className="sticky top-0 z-10 flex h-16 items-center gap-3 border-b border-border bg-surface px-4 md:h-18 md:px-8">
          <button
            type="button"
            aria-label="Mở menu"
            onClick={() => drawerRef.current?.showModal()}
            className="-ml-2 grid size-11 place-items-center rounded-sm text-ink hover:bg-primary-tint md:hidden"
          >
            <Icon name="menu" />
          </button>
          <Logo className="md:hidden" />
          <div className="ml-auto flex items-center gap-3">
            <Link
              to="/account"
              title="Tài khoản"
              className="flex items-center gap-3 rounded-sm p-1 hover:bg-primary-tint"
            >
              <span className="hidden text-right text-sm leading-tight sm:block">
                <span className="block font-semibold text-ink">{user?.fullName}</span>
                <span className="text-ink-muted">{admin ? 'Quản trị viên' : 'Giáo viên'}</span>
              </span>
              <span
                aria-hidden="true"
                className="grid size-10 place-items-center rounded-full bg-primary-tint font-semibold text-primary-deep"
              >
                {initials(user?.fullName)}
              </span>
              <span className="sr-only sm:hidden">Tài khoản</span>
            </Link>
            <button
              type="button"
              onClick={() => void logout()}
              aria-label="Đăng xuất"
              title="Đăng xuất"
              className={button('ghost', 'sm')}
            >
              <Icon name="logout" />
              <span className="hidden lg:inline">Đăng xuất</span>
            </button>
          </div>
        </header>
        <main id="main" tabIndex={-1} className="mx-auto max-w-[75rem] px-4 py-6 outline-none md:px-8 md:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

function Nav({ admin }: { admin: boolean }) {
  const item = ({ isActive }: { isActive: boolean }) => {
    const base = 'flex h-11 items-center gap-3 rounded-sm px-3 font-semibold transition-colors'
    if (admin) {
      return `${base} focus-visible:outline-accent ${
        isActive ? 'bg-primary text-white' : 'text-on-primary-muted hover:bg-white/10 hover:text-white'
      }`
    }
    return `${base} ${isActive ? 'bg-primary-tint text-primary' : 'text-ink-muted hover:bg-page hover:text-ink'}`
  }

  return (
    <nav aria-label="Chính" className="flex flex-col gap-1 px-3 py-2">
      {links
        .filter((link) => admin || !link.adminOnly)
        .map((link) => (
          <NavLink key={link.to} to={link.to} className={item}>
            <Icon name={link.icon} />
            {link.label}
          </NavLink>
        ))}
    </nav>
  )
}

/** "Nguyễn Minh Anh" -> "MA": the last two words, as Vietnamese names put the given name last. */
function initials(name = '') {
  return name
    .trim()
    .split(/\s+/)
    .slice(-2)
    .map((word) => word[0]?.toUpperCase() ?? '')
    .join('')
}
