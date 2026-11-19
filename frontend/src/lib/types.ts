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
