import { badge } from '../components/ui'
import type { AssignmentStatus } from '../lib/types'
import { statusLabel, statusTone } from './api'

export function StatusBadge({ status }: { status: AssignmentStatus }) {
  return <span className={badge(statusTone[status])}>{statusLabel[status]}</span>
}
