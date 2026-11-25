import type { InputHTMLAttributes, ReactNode } from 'react'
import { button, input, label as labelClass } from './ui'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

/** Label + input + error. The error is linked with aria-describedby so screen readers read it. */
export function Field({ label, error, hint, id, ...props }: FieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className={labelClass}>
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={input}
        {...props}
      />
      {error ? (
        <p id={`${id}-error`} className="text-sm text-danger">
          {error}
        </p>
      ) : (
        hint && (
          <p id={`${id}-hint`} className="text-sm text-ink-muted">
            {hint}
          </p>
        )
      )}
    </div>
  )
}

export function Logo({ className = '' }: { className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2 font-bold text-brand ${className}`}>
      <span aria-hidden="true" className="grid size-8 place-items-center rounded-sm bg-primary text-white">
        S
      </span>
      SmartTeach
    </span>
  )
}

/** Centered card used by sign-in, sign-up and the student entry pages. */
export function AuthCard({
  title,
  description,
  children,
  footer,
}: {
  title: string
  description?: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-6 px-4 py-10">
      <Logo className="text-lg" />
      <div className="w-full max-w-md rounded-lg border border-border bg-surface p-6 shadow-raised sm:p-8">
        <h1 className="text-2xl font-bold text-ink">{title}</h1>
        {description && <p className="mt-1 text-ink-muted">{description}</p>}
        <div className="mt-6">{children}</div>
      </div>
      {footer && <div className="text-center text-sm text-ink-muted">{footer}</div>}
    </main>
  )
}

export function SubmitButton({ pending, children }: { pending: boolean; children: ReactNode }) {
  return (
    <button type="submit" disabled={pending} className={`${button('primary')} w-full`}>
      {pending ? 'Đang xử lý…' : children}
    </button>
  )
}

export function FormError({ message }: { message?: string }) {
  if (!message) return null
  return (
    <p role="alert" className="rounded-sm border border-danger/30 bg-danger/5 px-3 py-2.5 text-sm text-danger">
      {message}
    </p>
  )
}
