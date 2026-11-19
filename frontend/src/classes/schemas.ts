import { z } from 'zod'

export const MAX_STUDENTS = 100

// Same limits as the backend (1–100 characters after trimming).
const name = (empty: string) => z.string().trim().min(1, empty).max(100, 'Tối đa 100 ký tự')

export const classNameSchema = z.object({ name: name('Nhập tên lớp') })
export const studentNameSchema = z.object({ name: name('Nhập tên học sinh') })

/** Textarea with one name per line; blank lines are ignored. */
export const studentListSchema = z.object({
  names: z
    .string()
    .transform((text) =>
      text
        .split('\n')
        .map((line) => line.trim())
        .filter(Boolean),
    )
    .pipe(
      z
        .array(z.string().max(100, 'Mỗi tên tối đa 100 ký tự'))
        .min(1, 'Nhập ít nhất một tên')
        .max(MAX_STUDENTS, `Tối đa ${MAX_STUDENTS} học sinh mỗi lần`),
    ),
})

export type NameForm = z.infer<typeof classNameSchema>
