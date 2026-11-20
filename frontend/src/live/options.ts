import type { LiveQuestion } from '../lib/types'

/** One colour and letter per option, the same on the projector and on the phones. */
export const optionColors = [
  'bg-rose-500',
  'bg-sky-500',
  'bg-amber-500',
  'bg-emerald-600',
  'bg-violet-500',
  'bg-pink-500',
  'bg-teal-600',
  'bg-orange-500',
]
export const optionLetter = (index: number) => String.fromCharCode(65 + index)

/** The answer of a revealed question in words, for short text and for a quick summary. */
export function correctAnswerText(question: LiveQuestion) {
  const reveal = question.reveal
  if (!reveal) return ''
  return question.kind === 'short_text'
    ? reveal.acceptedAnswers.join(' / ')
    : reveal.correctOptions.map((i) => `${optionLetter(i)}. ${question.options[i]}`).join(', ')
}
