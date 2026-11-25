import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { FormError } from '../components/form'
import { button, card } from '../components/ui'
import { problemOf } from '../lib/api'
import type { QuizSummary } from '../lib/types'
import { hostApi } from '../live/api'
import { quizApi, quizKeys } from './api'
import { MAX_QUIZZES } from './schemas'

const dateFormat = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

export function QuizzesPage() {
  const [page, setPage] = useState(0)
  const quizzes = useQuery({
    queryKey: quizKeys.list(page),
    queryFn: () => quizApi.list(page),
    placeholderData: keepPreviousData,
  })

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Quiz</h1>
          <p className="text-sm text-slate-500">
            Tối đa {MAX_QUIZZES} quiz, mỗi quiz tối đa 50 câu hỏi.
          </p>
        </div>
        <Link to="/quizzes/new" className={button('primary')}>
          Tạo quiz
        </Link>
      </div>

      {quizzes.isPending && <p className="text-slate-500">Đang tải…</p>}
      {quizzes.isError && <FormError message="Không tải được danh sách quiz." />}
      {quizzes.data?.totalElements === 0 && (
        <div className={`${card} text-center text-slate-500`}>Chưa có quiz nào. Bấm “Tạo quiz” để bắt đầu.</div>
      )}

      {quizzes.data && quizzes.data.content.length > 0 && (
        <ul className="space-y-3">
          {quizzes.data.content.map((quiz) => (
            <QuizRow key={quiz.id} quiz={quiz} onCopied={() => setPage(0)} />
          ))}
        </ul>
      )}

      {quizzes.data && quizzes.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <button
            type="button"
            className={button('secondary')}
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
          >
            Trước
          </button>
          <span className="text-slate-600">
            Trang {page + 1} / {quizzes.data.totalPages}
          </span>
          <button
            type="button"
            className={button('secondary')}
            disabled={page + 1 >= quizzes.data.totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Sau
          </button>
        </div>
      )}
    </div>
  )
}

function QuizRow({ quiz, onCopied }: { quiz: QuizSummary; onCopied: () => void }) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: quizKeys.all })

  const duplicate = useMutation({
    mutationFn: () => quizApi.duplicate(quiz.id),
    onSuccess: () => {
      onCopied()
      return refresh()
    },
  })
  const remove = useMutation({ mutationFn: () => quizApi.remove(quiz.id), onSuccess: refresh })
  const navigate = useNavigate()
  const play = useMutation({
    mutationFn: () => hostApi.create(quiz.id),
    onSuccess: (room) => navigate(`/live/${room.pin}`),
  })

  const error = duplicate.error
    ? problemOf(duplicate.error)?.status === 409
      ? `Bạn đã có ${MAX_QUIZZES} quiz, hãy xoá bớt quiz cũ.`
      : 'Không nhân bản được quiz.'
    : remove.error
      ? 'Không xoá được quiz.'
      : play.error
        ? 'Không mở được phòng chơi, hãy thử lại.'
        : undefined

  return (
    <li className={`${card} space-y-2`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to={`/quizzes/${quiz.id}`} className="min-w-0 flex-1 hover:text-blue-700">
          <p className="truncate text-lg font-semibold text-slate-900">{quiz.title}</p>
          <p className="text-sm text-slate-500">
            {quiz.questionCount} câu hỏi · Sửa lần cuối {dateFormat.format(new Date(quiz.updatedAt))}
          </p>
        </Link>
        <div className="flex gap-2">
          <button
            type="button"
            className={button('primary')}
            disabled={play.isPending || quiz.questionCount === 0}
            title="Mở phòng có mã PIN, học sinh vào chơi trên điện thoại"
            onClick={() => play.mutate()}
          >
            Chơi trực tiếp
          </button>
          <Link to={`/quizzes/${quiz.id}`} className={button('secondary')}>
            Sửa
          </Link>
          <button
            type="button"
            className={button('secondary')}
            disabled={duplicate.isPending}
            onClick={() => duplicate.mutate()}
          >
            Nhân bản
          </button>
          <button
            type="button"
            className={button('ghostDanger')}
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm(`Xoá quiz "${quiz.title}"? Bài tập đã giao vẫn giữ nguyên câu hỏi.`)) remove.mutate()
            }}
          >
            Xoá
          </button>
        </div>
      </div>
      {error && <FormError message={error} />}
    </li>
  )
}
