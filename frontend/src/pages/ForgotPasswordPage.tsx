import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { ColdStartHint } from '../components/states'
import { link } from '../components/ui'
import { api, tooManyRequestsMessage } from '../lib/api'

const schema = z.object({ email: z.email('Email không hợp lệ').max(254, 'Email quá dài') })

type FormValues = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const { register, handleSubmit, formState, getValues } = useForm<FormValues>({ resolver: zodResolver(schema) })

  const mutation = useMutation({
    mutationFn: async ({ email }: FormValues) => {
      await api.post('/api/auth/forgot-password', { email })
    },
  })

  const footer = (
    <Link to="/login" className={link}>
      Quay lại đăng nhập
    </Link>
  )

  // The backend answers 204 whether or not the email has an account, so the page says the same.
  if (mutation.isSuccess) {
    return (
      <AuthCard title="Kiểm tra email" description="Liên kết đặt lại mật khẩu có hạn 30 phút" footer={footer}>
        <p className="text-sm text-ink-muted">
          Nếu <strong className="text-ink">{getValues('email')}</strong> có tài khoản SmartTeach, một email chứa liên
          kết đặt lại mật khẩu vừa được gửi tới. Hãy xem cả thư mục Spam.
        </p>
      </AuthCard>
    )
  }

  const errorMessage = mutation.error
    ? (tooManyRequestsMessage(mutation.error) ?? 'Có lỗi xảy ra, vui lòng thử lại')
    : undefined

  return (
    <AuthCard title="Quên mật khẩu" description="Nhập email bạn đã dùng để đăng ký" footer={footer}>
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
        <SubmitButton pending={mutation.isPending}>Gửi liên kết đặt lại</SubmitButton>
        {mutation.isPending && <ColdStartHint />}
      </form>
    </AuthCard>
  )
}
