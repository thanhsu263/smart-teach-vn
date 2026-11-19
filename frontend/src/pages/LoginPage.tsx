import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../auth/auth-context'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { problemOf } from '../lib/api'

const schema = z.object({
  email: z.email('Email không hợp lệ'),
  password: z.string().min(1, 'Vui lòng nhập mật khẩu'),
})

type FormValues = z.infer<typeof schema>

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
    <AuthCard title="Đăng nhập">
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
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        Chưa có tài khoản?{' '}
        <Link to="/register" className="font-medium text-blue-600 hover:underline">
          Đăng ký
        </Link>
      </p>
    </AuthCard>
  )
}
