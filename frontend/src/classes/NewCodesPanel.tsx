import { useState } from 'react'
import { button } from '../components/ui'
import type { StudentCode } from '../lib/types'

/** Shows freshly created access codes. The server keeps only hashes, so this is the only chance to copy them. */
export function NewCodesPanel({ codes, onClose }: { codes: StudentCode[]; onClose: () => void }) {
  const [copied, setCopied] = useState(false)

  const copyAll = async () => {
    // Tab-separated, so it pastes into two columns in Excel / Google Sheets.
    await navigator.clipboard.writeText(codes.map((c) => `${c.name}\t${c.accessCode}`).join('\n'))
    setCopied(true)
  }

  const close = () => {
    if (copied || window.confirm('Mã sẽ không xem lại được. Bạn đã chép mã chưa?')) onClose()
  }

  return (
    <section role="status" className="rounded-2xl bg-amber-50 p-5 ring-1 ring-amber-300">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="font-semibold text-amber-900">Mã truy cập mới</h2>
          <p className="text-sm text-amber-800">
            Mã chỉ hiện một lần. Hãy chép lại và gửi cho học sinh trước khi đóng.
          </p>
        </div>
        <div className="flex gap-2">
          <button type="button" className={button.primary} onClick={() => void copyAll()}>
            {copied ? 'Đã chép' : 'Chép tất cả'}
          </button>
          <button type="button" className={button.secondary} onClick={close}>
            Đóng
          </button>
        </div>
      </div>
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">
        {codes.map((c) => (
          <li key={c.id} className="flex items-center justify-between gap-3 rounded-lg bg-white px-3 py-2">
            <span className="truncate text-slate-900">{c.name}</span>
            <span className="font-mono text-lg font-semibold tracking-wider text-slate-900">{c.accessCode}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
