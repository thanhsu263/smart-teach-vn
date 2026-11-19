export type Role = 'TEACHER' | 'ADMIN'

export interface User {
  id: number
  email: string
  fullName: string
  role: Role
}

export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  user: User
}

/** RFC 9457 error body returned by the backend. */
export interface ProblemDetail {
  status: number
  detail?: string
  errors?: Record<string, string>
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ClassItem {
  id: number
  name: string
  studentCount: number
  createdAt: string
}

export interface Student {
  id: number
  name: string
  position: number
  codeHint: string
  createdAt: string
}

/** Only returned right after a code is created; the plain code cannot be fetched again. */
export interface StudentCode {
  id: number
  name: string
  position: number
  accessCode: string
  codeHint: string
}

export type QuestionKind = 'single' | 'true_false' | 'multi' | 'short_text'

export interface QuizSummary {
  id: number
  title: string
  questionCount: number
  createdAt: string
  updatedAt: string
}

export interface QuizOption {
  label: string
  correct: boolean
}

export interface Question {
  position: number
  kind: QuestionKind
  prompt: string
  timeLimitSeconds: number
  ignoreDiacritics: boolean
  options: QuizOption[]
  acceptedAnswers: string[]
}

export interface Quiz {
  id: number
  title: string
  createdAt: string
  updatedAt: string
  questions: Question[]
}

/** Body of POST/PUT /api/quizzes. Positions come from the array order. */
export interface QuizPayload {
  title: string
  questions: Omit<Question, 'position'>[]
}
