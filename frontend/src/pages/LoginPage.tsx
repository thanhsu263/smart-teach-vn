import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../auth/auth-context'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { ColdStartHint } from '../components/states'
import { button, link } from '../components/ui'
import { problemOf } from '../lib/api'

const schema = z.object({
  email: z.email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})

type FormValues = z.infer<typeof schema>

// Set on the public demo site only, so visitors can look around without registering.
const demoAccount =
  import.meta.env.VITE_DEMO_EMAIL && import.meta.env.VITE_DEMO_PASSWORD
    ? { email: import.meta.env.VITE_DEMO_EMAIL, password: import.meta.env.VITE_DEMO_PASSWORD }
    : null

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: () => navigate(from, { replace: true }),
  })

  const errorMessage = mutation.error
    ? problemOf(mutation.error)?.status === 401
      ? 'Email hoặc mật khẩu không đúng'
      : 'Có lỗi xảy ra, vui lòng thử lại'
    : undefined

  return (
    <AuthCard
      title="Đăng nhập"
      description="Dành cho giáo viên"
      footer={
        <>
          Học sinh?{' '}
          <Link to="/s" className={link}>
            Vào làm bài tập
          </Link>{' '}
          hoặc{' '}
          <Link to="/play" className={link}>
            chơi quiz trực tiếp
          </Link>
        </>
      }
    >
      <form className="space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values))}>
        <FormError message={errorMessage} />
        <Field
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          error={formState.errors.email?.message}
          {...register('email')}
        />
        <Field
          id="password"
          label="Mật khẩu"
          type="password"
          autoComplete="current-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <SubmitButton pending={mutation.isPending}>Đăng nhập</SubmitButton>
        {mutation.isPending && <ColdStartHint />}
      </form>

      {demoAccount && (
        <div className="mt-4 space-y-4">
          <p className="flex items-center gap-3 text-sm text-ink-muted before:h-px before:flex-1 before:bg-border after:h-px after:flex-1 after:bg-border">
            hoặc
          </p>
          <button
            type="button"
            className={`${button('secondary')} w-full`}
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(demoAccount)}
          >
            Dùng tài khoản demo
          </button>
        </div>
      )}

      <p className="mt-6 text-center text-sm text-ink-muted">
        Chưa có tài khoản?{' '}
        <Link to="/register" className={link}>
          Đăng ký
        </Link>
      </p>
    </AuthCard>
  )
}
