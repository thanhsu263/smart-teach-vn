import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useConfirm } from '../components/confirm-context'
import { FormError } from '../components/form'
import { Icon } from '../components/Icon'
import { PageHeader, Pagination } from '../components/page'
import { EmptyState, QueryState } from '../components/states'
import { button, card, muted } from '../components/ui'
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

  const newQuiz = (
    <Link to="/quizzes/new" className={button('primary')}>
      <Icon name="plus" />
      Tạo quiz
    </Link>
  )

  return (
    <div className="space-y-6">
      <PageHeader
        title="Quiz"
        description={`Tối đa ${MAX_QUIZZES} quiz, mỗi quiz tối đa 50 câu hỏi.`}
        actions={newQuiz}
      />

      <QueryState
        query={quizzes}
        error="Không tải được danh sách quiz."
        isEmpty={(data) => data.totalElements === 0}
        empty={
          <EmptyState
            icon="quiz"
            title="Chưa có quiz nào"
            description="Soạn bộ câu hỏi một lần, rồi giao làm bài tập cho lớp hoặc chơi trực tiếp bằng mã PIN."
            action={newQuiz}
          />
        }
      >
        {(data) => (
          <>
            <ul className="space-y-3">
              {data.content.map((quiz) => (
                <QuizRow key={quiz.id} quiz={quiz} onCopied={() => setPage(0)} />
              ))}
            </ul>
            <Pagination page={page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </QueryState>
    </div>
  )
}

function QuizRow({ quiz, onCopied }: { quiz: QuizSummary; onCopied: () => void }) {
  const queryClient = useQueryClient()
  const confirm = useConfirm()
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

  const confirmRemove = async () => {
    const ok = await confirm({
      title: `Xoá quiz "${quiz.title}"?`,
      message: 'Bài tập đã giao vẫn giữ nguyên câu hỏi.',
      confirmLabel: 'Xoá quiz',
      danger: true,
    })
    if (ok) remove.mutate()
  }

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
    <li className={`${card} space-y-3`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Link to={`/quizzes/${quiz.id}`} className="group min-w-0 flex-1 rounded-sm">
          <p className="truncate text-heading text-ink group-hover:text-primary">{quiz.title}</p>
          <p className={muted}>
            {quiz.questionCount} câu hỏi · Sửa lần cuối {dateFormat.format(new Date(quiz.updatedAt))}
          </p>
        </Link>
        <div className="flex flex-wrap gap-1">
          <button
            type="button"
            className={button('primary', 'sm')}
            disabled={play.isPending || quiz.questionCount === 0}
            title="Mở phòng có mã PIN, học sinh vào chơi trên điện thoại"
            onClick={() => play.mutate()}
          >
            <Icon name="play" className="size-4" />
            Chơi trực tiếp
          </button>
          <Link to={`/quizzes/${quiz.id}`} className={button('ghost', 'sm')}>
            Sửa
          </Link>
          <button
            type="button"
            className={button('ghost', 'sm')}
            disabled={duplicate.isPending}
            onClick={() => duplicate.mutate()}
          >
            Nhân bản
          </button>
          <button
            type="button"
            className={button('ghostDanger', 'sm')}
            disabled={remove.isPending}
            onClick={() => void confirmRemove()}
          >
            Xoá
          </button>
        </div>
      </div>
      {error && <FormError message={error} />}
    </li>
  )
}
