import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '../auth/auth-context'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { problemOf } from '../lib/api'

// Mirrors the backend rules so most mistakes are caught before the request.
const schema = z
  .object({
    fullName: z.string().trim().min(1, 'Vui lòng nhập họ tên').max(100, 'Tối đa 100 ký tự'),
    email: z.email('Email không hợp lệ').max(254, 'Email quá dài'),
    password: z
      .string()
      .min(8, 'Mật khẩu tối thiểu 8 ký tự')
      .refine((value) => new TextEncoder().encode(value).length <= 72, 'Mật khẩu quá dài'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Mật khẩu nhập lại không khớp',
  })

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { register: signUp } = useAuth()
  const navigate = useNavigate()
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const mutation = useMutation({
    mutationFn: ({ fullName, email, password }: FormValues) => signUp({ fullName, email, password }),
    onSuccess: () => navigate('/', { replace: true }),
  })

  const status = problemOf(mutation.error)?.status
  const errorMessage = !mutation.error
    ? undefined
    : status === 409
      ? 'Email này đã được đăng ký'
      : status === 400
        ? 'Thông tin chưa hợp lệ, vui lòng kiểm tra lại'
        : 'Có lỗi xảy ra, vui lòng thử lại'

  return (
    <AuthCard title="Tạo tài khoản giáo viên">
      <form className="space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values))}>
        <FormError message={errorMessage} />
        <Field
          id="fullName"
          label="Họ và tên"
          autoComplete="name"
          error={formState.errors.fullName?.message}
          {...register('fullName')}
        />
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
          autoComplete="new-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <Field
          id="confirmPassword"
          label="Nhập lại mật khẩu"
          type="password"
          autoComplete="new-password"
          error={formState.errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <SubmitButton pending={mutation.isPending}>Đăng ký</SubmitButton>
      </form>
      <p className="mt-6 text-center text-sm text-slate-600">
        Đã có tài khoản?{' '}
        <Link to="/login" className="font-medium text-blue-600 hover:underline">
          Đăng nhập
        </Link>
      </p>
    </AuthCard>
  )
}
