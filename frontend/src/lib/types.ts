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

export type AssignmentStatus = 'draft' | 'published' | 'closed'
export type RevealPolicy = 'immediate' | 'after_close'
export type AttemptStatus = 'in_progress' | 'submitted' | 'invalidated'

export interface AssignmentSummary {
  id: number
  classId: number
  className: string
  title: string
  publicCode: string
  status: AssignmentStatus
  opensAt: string | null
  dueAt: string | null
  createdAt: string
}

export interface Assignment {
  id: number
  classId: number
  className: string | null
  quizId: number | null
  title: string
  description: string
  publicCode: string
  opensAt: string | null
  dueAt: string | null
  revealPolicy: RevealPolicy
  status: AssignmentStatus
  /** Null until published: the questions are copied from the quiz at that moment. */
  questionCount: number | null
  createdAt: string
  updatedAt: string
  closedAt: string | null
}

export interface AssignmentPayload {
  classId: number
  quizId: number
  title: string
  description: string
  opensAt: string | null
  dueAt: string | null
  revealPolicy: RevealPolicy
}

export interface Analytics {
  studentCount: number
  submittedCount: number
  inProgressCount: number
  averageScore: number | null
  questions: { position: number; prompt: string; answered: number; correct: number; correctRate: number | null }[]
  students: {
    studentId: number
    name: string
    status: 'not_started' | AttemptStatus
    score: number | null
    correctCount: number | null
    submittedAt: string | null
  }[]
}

/** single/true_false: option index; multi: list of indexes; short_text: text. */
export type AnswerValue = number | number[] | string

export interface AttemptView {
  attemptId: number
  assignmentTitle: string
  description: string
  studentName: string
  status: AttemptStatus
  open: boolean
  dueAt: string | null
  revealPolicy: RevealPolicy
  questions: { position: number; kind: QuestionKind; prompt: string; options: string[] }[]
  answers: Record<string, AnswerValue>
}

export interface AttemptResult {
  attemptId: number
  assignmentTitle: string
  studentName: string
  status: AttemptStatus
  submittedAt: string | null
  /** False when the reveal policy hides the score until the assignment ends. */
  revealed: boolean
  score: number | null
  correctCount: number | null
  questionTotal: number | null
  questions: {
    position: number
    kind: QuestionKind
    prompt: string
    options: QuizOption[]
    acceptedAnswers: string[]
    answer: AnswerValue | null
    correct: boolean
  }[]
}
