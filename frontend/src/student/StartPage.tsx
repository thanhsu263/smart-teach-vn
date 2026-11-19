import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { AuthCard, Field, FormError, SubmitButton } from '../components/form'
import { problemOf } from '../lib/api'
import { studentApi } from './api'

const startSchema = z.object({
  publicCode: z.string().trim().min(1, 'Nhập mã bài'),
  accessCode: z.string().trim().min(1, 'Nhập mã học sinh').max(50, 'Mã quá dài'),
})
type StartForm = z.infer<typeof startSchema>

/** /s and /s/:code – the link the teacher shares already contains the assignment code. */
export function StartPage() {
  const navigate = useNavigate()
  const code = useParams().code ?? ''
  const { register, handleSubmit, formState } = useForm<StartForm>({
    resolver: zodResolver(startSchema),
    defaultValues: { publicCode: code, accessCode: '' },
  })

  const start = useMutation({
    mutationFn: (form: StartForm) => studentApi.start(form.publicCode, form.accessCode),
    onSuccess: (attempt) => navigate(`/attempts/${attempt.attemptId}`),
  })

  const problem = problemOf(start.error)
  const error = !start.error
    ? undefined
    : problem?.status === 404
      ? 'Mã bài hoặc mã học sinh không đúng.'
      : problem?.status === 409
        ? problem.detail?.includes('not open')
          ? 'Bài tập chưa mở, hãy quay lại sau.'
          : 'Bài tập đã đóng hoặc đã hết hạn.'
        : 'Không vào được bài, hãy thử lại.'

  return (
    <AuthCard title="Làm bài tập">
      <form onSubmit={handleSubmit((form) => start.mutate(form))} className="space-y-4" noValidate>
        <FormError message={error} />
        <Field
          id="publicCode"
          label="Mã bài"
          autoComplete="off"
          error={formState.errors.publicCode?.message}
          {...register('publicCode')}
        />
        <Field
          id="accessCode"
          label="Mã học sinh (thầy cô đã phát)"
          autoComplete="off"
          autoFocus={code !== ''}
          error={formState.errors.accessCode?.message}
          {...register('accessCode')}
        />
        <SubmitButton pending={start.isPending}>Vào làm bài</SubmitButton>
      </form>
    </AuthCard>
  )
}
