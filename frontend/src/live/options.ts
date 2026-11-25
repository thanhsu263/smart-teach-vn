import type { LiveQuestion } from '../lib/types'

/** One colour and letter per option, the same on the projector and on the phones (700 shades: white text stays readable, 4.5:1). */
export const optionColors = [
  'bg-rose-700',
  'bg-sky-700',
  'bg-amber-700',
  'bg-emerald-700',
  'bg-violet-700',
  'bg-pink-700',
  'bg-teal-700',
  'bg-orange-700',
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
