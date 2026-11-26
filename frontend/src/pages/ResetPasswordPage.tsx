import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { button, link } from '../components/ui'
import { api, problemOf, tooManyRequestsMessage } from '../lib/api'

const schema = z
  .object({
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

/** The mail link is /reset-password#token=...; the fragment is never sent to a server. */
function tokenFromHash() {
  return new URLSearchParams(window.location.hash.slice(1)).get('token')
}

export function ResetPasswordPage() {
  const [token] = useState(tokenFromHash)
  const { register, handleSubmit, formState } = useForm<FormValues>({ resolver: zodResolver(schema) })

  // Drop the token from the address bar so it is not copied or left in the history.
  useEffect(() => {
    if (window.location.hash) window.history.replaceState(null, '', window.location.pathname)
  }, [])

  const mutation = useMutation({
    mutationFn: async ({ password }: FormValues) => {
      await api.post('/api/auth/reset-password', { token, newPassword: password })
    },
  })

  const footer = (
    <Link to="/login" className={link}>
      Quay lại đăng nhập
    </Link>
  )

  if (!token) {
    return (
      <AuthCard title="Liên kết không hợp lệ" description="Không tìm thấy mã đặt lại mật khẩu" footer={footer}>
        <p className="mb-4 text-sm text-ink-muted">Hãy mở đúng liên kết trong email, hoặc yêu cầu một liên kết mới.</p>
        <Link to="/forgot-password" className={`${button('primary')} w-full`}>
          Gửi lại liên kết
        </Link>
      </AuthCard>
    )
  }

  if (mutation.isSuccess) {
    return (
      <AuthCard title="Đã đổi mật khẩu" description="Mọi phiên đăng nhập cũ đã bị đăng xuất">
        <Link to="/login" className={`${button('primary')} w-full`}>
          Đăng nhập với mật khẩu mới
        </Link>
      </AuthCard>
    )
  }

  const status = problemOf(mutation.error)?.status
  const linkInvalid = status === 400 && !problemOf(mutation.error)?.errors
  const errorMessage = !mutation.error
    ? undefined
    : status === 429
      ? tooManyRequestsMessage(mutation.error)
      : linkInvalid
        ? 'Liên kết đã hết hạn hoặc đã được dùng. Hãy yêu cầu một liên kết mới.'
        : status === 400
          ? 'Mật khẩu chưa hợp lệ, vui lòng kiểm tra lại'
          : 'Có lỗi xảy ra, vui lòng thử lại'

  return (
    <AuthCard title="Đặt mật khẩu mới" description="Liên kết chỉ dùng được một lần" footer={footer}>
      <form className="space-y-4" noValidate onSubmit={handleSubmit((values) => mutation.mutate(values))}>
        <FormError message={errorMessage} />
        {linkInvalid && (
          <Link to="/forgot-password" className={`${button('secondary')} w-full`}>
            Gửi lại liên kết
          </Link>
        )}
        <Field
          id="password"
          label="Mật khẩu mới"
          type="password"
          autoComplete="new-password"
          error={formState.errors.password?.message}
          {...register('password')}
        />
        <Field
          id="confirmPassword"
          label="Nhập lại mật khẩu mới"
          type="password"
          autoComplete="new-password"
          error={formState.errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />
        <SubmitButton pending={mutation.isPending}>Đổi mật khẩu</SubmitButton>
      </form>
    </AuthCard>
  )
}
