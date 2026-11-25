import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Icon } from './Icon'
import { button } from './ui'

/** Title row of a page: optional back link, the page's h1, a short description and actions. */
export function PageHeader({
  title,
  description,
  actions,
  back,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  back?: { to: string; label: string }
}) {
  return (
    <header className="space-y-2">
      {back && (
        <Link
          to={back.to}
          className="inline-flex items-center gap-1 rounded-sm text-sm font-semibold text-primary hover:underline"
        >
          <Icon name="back" className="size-4" />
          {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="min-w-0 space-y-1">
          <h1 className="text-2xl font-bold break-words text-ink md:text-title">{title}</h1>
          {description && <div className="text-ink-muted">{description}</div>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </header>
  )
}

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Phân trang" className="flex items-center justify-center gap-3">
      <button
        type="button"
        className={button('secondary', 'sm')}
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        <Icon name="back" className="size-4" />
        Trước
      </button>
      <span className="text-sm text-ink-muted" aria-current="page">
        Trang {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        className={button('secondary', 'sm')}
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Sau
        <Icon name="next" className="size-4" />
      </button>
    </nav>
  )
}
