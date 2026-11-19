import { api } from '../lib/api'
import type { ClassItem, Page, Student, StudentCode } from '../lib/types'

export const PAGE_SIZE = 12

export const classKeys = {
  all: ['classes'] as const,
  lists: ['classes', 'list'] as const,
  list:(page: number) => ['classes', 'list', page] as const,
  detail: (id: number) => ['classes', 'detail', id] as const,
  students: (id: number) => ['classes', 'students', id] as const,
}

export const classApi = {
  list: async (page: number) =>
    (await api.get<Page<ClassItem>>('/api/classes', { params: { page, size: PAGE_SIZE } })).data,
  get: async (id: number) => (await api.get<ClassItem>(`/api/classes/${id}`)).data,
  create: async (name: string) => (await api.post<ClassItem>('/api/classes', { name })).data,
  rename: async (id: number, name: string) => (await api.put<ClassItem>(`/api/classes/${id}`, { name })).data,
  remove: async (id: number) => {
    await api.delete(`/api/classes/${id}`)
  },

  students: async (classId: number) => (await api.get<Student[]>(`/api/classes/${classId}/students`)).data,
  addStudents: async (classId: number, names: string[]) =>
    (await api.post<StudentCode[]>(`/api/classes/${classId}/students`, { names })).data,
  renameStudent: async (id: number, name: string) =>
    (await api.put<Student>(`/api/students/${id}`, { name })).data,
  removeStudent: async (id: number) => {
    await api.delete(`/api/students/${id}`)
  },
  regenerateCode: async (id: number) => (await api.post<StudentCode>(`/api/students/${id}/access-code`)).data,
}
