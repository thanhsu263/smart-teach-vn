import { z } from 'zod'
import type { Question, QuestionKind, Quiz, QuizPayload } from '../lib/types'

// Same limits as QuizRules on the backend; the server checks again on save.
export const MAX_QUIZZES = 50
export const MAX_QUESTIONS = 50
export const MAX_OPTIONS = 6
export const MAX_ANSWERS = 10
export const TIME_LIMITS = [10, 20, 30, 60, 90] as const

export const KIND_LABELS: Record<QuestionKind, string> = {
  single: 'Một đáp án',
  multi: 'Nhiều đáp án',
  true_false: 'Đúng / Sai',
  short_text: 'Trả lời ngắn',
}

const optionSchema = z.object({ label: z.string(), correct: z.boolean() })

const questionSchema = z
  .object({
    kind: z.enum(['single', 'true_false', 'multi', 'short_text']),
    prompt: z.string().trim().min(1, 'Nhập nội dung câu hỏi').max(500, 'Tối đa 500 ký tự'),
    timeLimitSeconds: z.number(),
    ignoreDiacritics: z.boolean(),
    options: z.array(optionSchema),
    // Objects, not strings: useFieldArray needs an object per row.
    acceptedAnswers: z.array(z.object({ value: z.string() })),
  })
  .superRefine((q, ctx) => {
    const issue = (path: (string | number)[], message: string) => ctx.addIssue({ code: 'custom', path, message })

    if (q.kind === 'short_text') {
      const answers = q.acceptedAnswers.map((a) => a.value.trim())
      answers.forEach((a, i) => {
        if (!a) issue(['acceptedAnswers', i, 'value'], 'Nhập đáp án')
        else if (a.length > 100) issue(['acceptedAnswers', i, 'value'], 'Tối đa 100 ký tự')
      })
      if (answers.length === 0) issue(['acceptedAnswers'], 'Thêm ít nhất một đáp án chấp nhận')
      else if (answers.length > MAX_ANSWERS) issue(['acceptedAnswers'], `Tối đa ${MAX_ANSWERS} đáp án`)
      else if (hasDuplicates(answers)) issue(['acceptedAnswers'], 'Các đáp án phải khác nhau')
      return
    }

    const labels = q.options.map((o) => o.label.trim())
    labels.forEach((label, i) => {
      if (!label) issue(['options', i, 'label'], 'Nhập nội dung lựa chọn')
      else if (label.length > 150) issue(['options', i, 'label'], 'Tối đa 150 ký tự')
    })
    const correct = q.options.filter((o) => o.correct).length
    if (q.kind === 'true_false' && q.options.length !== 2) issue(['options'], 'Câu Đúng/Sai có đúng 2 lựa chọn')
    else if (q.options.length < 2) issue(['options'], 'Thêm ít nhất 2 lựa chọn')
    else if (q.options.length > MAX_OPTIONS) issue(['options'], `Tối đa ${MAX_OPTIONS} lựa chọn`)
    else if (q.kind === 'multi' && correct < 1) issue(['options'], 'Đánh dấu ít nhất một đáp án đúng')
    else if (q.kind !== 'multi' && correct !== 1) issue(['options'], 'Chỉ đánh dấu một đáp án đúng')
    else if (hasDuplicates(labels)) issue(['options'], 'Các lựa chọn phải khác nhau')
  })

export const quizFormSchema = z.object({
  title: z.string().trim().min(1, 'Nhập tên quiz').max(100, 'Tối đa 100 ký tự'),
  questions: z.array(questionSchema).max(MAX_QUESTIONS, `Tối đa ${MAX_QUESTIONS} câu hỏi`),
})

export type QuizForm = z.infer<typeof quizFormSchema>
export type QuestionForm = QuizForm['questions'][number]

function hasDuplicates(values: string[]) {
  const seen = values.filter(Boolean).map((v) => v.toLowerCase())
  return new Set(seen).size !== seen.length
}

export const emptyOptions = () => Array.from({ length: 4 }, () => ({ label: '', correct: false }))
export const trueFalseOptions = () => [
  { label: 'Đúng', correct: false },
  { label: 'Sai', correct: false },
]

export const newQuestion = (): QuestionForm => ({
  kind: 'single',
  prompt: '',
  timeLimitSeconds: 20,
  ignoreDiacritics: false,
  options: emptyOptions(),
  acceptedAnswers: [],
})

export function toForm(quiz?: Quiz): QuizForm {
  if (!quiz) return { title: '', questions: [newQuestion()] }
  return {
    title: quiz.title,
    questions: quiz.questions.map((q) => ({
      kind: q.kind,
      prompt: q.prompt,
      timeLimitSeconds: q.timeLimitSeconds,
      ignoreDiacritics: q.ignoreDiacritics,
      options: q.options,
      acceptedAnswers: q.acceptedAnswers.map((value) => ({ value })),
    })),
  }
}

/** The form keeps options and answers while the kind changes; only send what the kind uses. */
export function toPayload(form: QuizForm): QuizPayload {
  return {
    title: form.title.trim(),
    questions: form.questions.map((q): Omit<Question, 'position'> => {
      const text = q.kind === 'short_text'
      return {
        kind: q.kind,
        prompt: q.prompt.trim(),
        timeLimitSeconds: q.timeLimitSeconds,
        ignoreDiacritics: text && q.ignoreDiacritics,
        options: text ? [] : q.options.map((o) => ({ label: o.label.trim(), correct: o.correct })),
        acceptedAnswers: text ? q.acceptedAnswers.map((a) => a.value.trim()) : [],
      }
    }),
  }
}
