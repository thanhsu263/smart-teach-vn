import type { UseQueryResult } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'
import { Icon, type IconName } from './Icon'
import { button } from './ui'

/** Grey block that shimmers while data loads. Size it with className (h-*, w-*). */
export function Skeleton({ className = 'h-4 w-full' }: { className?: string }) {
  return <div aria-hidden="true" className={`skeleton rounded-sm ${className}`} />
}

/** A few card-shaped skeletons, the default loading state for list pages. */
export function SkeletonList({ rows = 3 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Đang tải" className="space-y-3">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="space-y-3 rounded-md border border-border bg-surface p-5">
          <Skeleton className="h-5 w-2/5" />
          <Skeleton className="h-4 w-3/5" />
        </div>
      ))}
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon: IconName
  title: string
  description?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-md border border-dashed border-border bg-surface px-6 py-10 text-center">
      <span className="grid size-14 place-items-center rounded-full bg-primary-tint text-primary">
        <Icon name={icon} className="size-7" />
      </span>
      <h2 className="text-heading text-ink">{title}</h2>
      {description && <p className="max-w-md text-sm text-ink-muted">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-md border border-danger/30 bg-surface px-6 py-8 text-center"
    >
      <span className="grid size-14 place-items-center rounded-full bg-danger/10 text-danger">
        <Icon name="alert" className="size-7" />
      </span>
      <p className="font-semibold text-ink">{message}</p>
      {onRetry && (
        <button type="button" className={button('secondary', 'sm')} onClick={onRetry}>
          Thử lại
        </button>
      )}
    </div>
  )
}

/**
 * The four states of a page that loads data: loading, error, empty, data.
 * Pages pass what to show in each case, so none of them is forgotten.
 */
export function QueryState<T>({
  query,
  error,
  loading = <SkeletonList />,
  isEmpty,
  empty,
  children,
}: {
  query: UseQueryResult<T>
  error: string
  loading?: ReactNode
  isEmpty?: (data: T) => boolean
  empty?: ReactNode
  children: (data: T) => ReactNode
}) {
  if (query.isPending) return loading
  if (query.isError) return <ErrorState message={error} onRetry={() => void query.refetch()} />
  if (empty && isEmpty?.(query.data)) return empty
  return children(query.data)
}

export function Spinner({ className = 'size-6' }: { className?: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-block animate-spin rounded-full border-[3px] border-primary/25 border-t-primary ${className}`}
    />
  )
}

/** Free hosting puts the backend to sleep; the first request can take half a minute. Say so. */
export function ColdStartHint({ delayMs = 4000 }: { delayMs?: number }) {
  const [show, setShow] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setShow(true), delayMs)
    return () => clearTimeout(timer)
  }, [delayMs])
  if (!show) return null
  return (
    <p role="status" className="rounded-sm bg-accent/30 px-3 py-2 text-sm text-ink">
      Server đang khởi động, chờ khoảng 30 giây…
    </p>
  )
}

/** Whole-screen loading state, e.g. while the session is restored on first load. */
export function FullPageLoading() {
  return (
    <div role="status" className="grid min-h-screen place-items-center px-4">
      <div className="flex max-w-xs flex-col items-center gap-4 text-center">
        <Spinner className="size-8" />
        <span className="text-ink-muted">Đang tải…</span>
        <ColdStartHint />
      </div>
    </div>
  )
}
