// Shared Tailwind class strings built on the Figma tokens in index.css, so controls look the same on
// every page. They are plain strings (not components) so they work on <button>, <Link> and <a> alike.

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'ghostDanger'

const buttonVariants: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover active:bg-primary-deep',
  secondary: 'border border-primary bg-surface text-primary hover:bg-primary-tint',
  ghost: 'text-primary hover:bg-primary-tint',
  danger: 'bg-danger text-white hover:bg-danger/90',
  ghostDanger: 'text-danger hover:bg-danger/10',
}

// Controls are 44px high on phones and 48px from 720px up (Figma); "sm" is for buttons inside lists.
const buttonSizes = {
  md: 'h-11 px-5 md:h-12',
  sm: 'h-10 px-3.5 text-sm',
}

export function button(variant: ButtonVariant = 'primary', size: keyof typeof buttonSizes = 'md') {
  return `inline-flex shrink-0 items-center justify-center gap-2 rounded-sm font-semibold whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${buttonSizes[size]} ${buttonVariants[variant]}`
}

const control = 'rounded-sm border border-border-strong bg-surface text-ink aria-invalid:border-danger disabled:opacity-60'

export const input = `${control} h-11 w-full px-3 placeholder:text-ink-muted/70 md:h-12`

export const textarea = `${control} min-h-24 w-full px-3 py-2.5 placeholder:text-ink-muted/70`

export const select = `${control} h-11 px-3 md:h-12`

export const label = 'block text-sm font-semibold text-ink'

export const card = 'rounded-md border border-border bg-surface p-5 shadow-card'

export const muted = 'text-sm text-ink-muted'

type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'

const tones: Record<Tone, string> = {
  neutral: 'bg-page text-ink-muted ring-border',
  info: 'bg-primary-tint text-primary-deep ring-primary/25',
  success: 'bg-success/10 text-success ring-success/25',
  warning: 'bg-warning/10 text-warning ring-warning/25',
  danger: 'bg-danger/10 text-danger ring-danger/25',
}

export function badge(tone: Tone = 'neutral') {
  return `inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${tones[tone]}`
}
