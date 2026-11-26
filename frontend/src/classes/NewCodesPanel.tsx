import { useState } from 'react'
import { useConfirm } from '../components/confirm-context'
import { Icon } from '../components/Icon'
import { button } from '../components/ui'
import type { StudentCode } from '../lib/types'

/** Shows freshly created access codes. The server keeps only hashes, so this is the only chance to copy them. */
export function NewCodesPanel({ codes, onClose }: { codes: StudentCode[]; onClose: () => void }) {
  const [copied, setCopied] = useState(false)
  const confirm = useConfirm()

  const copyAll = async () => {
    // Tab-separated, so it pastes into two columns in Excel / Google Sheets.
    await navigator.clipboard.writeText(codes.map((c) => `${c.name}\t${c.accessCode}`).join('\n'))
    setCopied(true)
  }

  const close = async () => {
    if (
      copied ||
      (await confirm({
        title: 'Đóng khi chưa chép mã?',
        message: 'Mã chỉ hiện một lần. Sau khi đóng, muốn xem lại phải cấp mã mới cho học sinh.',
        confirmLabel: 'Đóng',
      }))
    ) {
      onClose()
    }
  }

  return (
    <section role="status" className="rounded-md border border-accent-deep bg-accent/25 p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-heading text-ink">Mã truy cập mới</h2>
          <p className="text-sm text-ink">Mã chỉ hiện một lần. Hãy chép lại và gửi cho học sinh trước khi đóng.</p>
        </div>
        <div className="flex gap-2">
          <button type="button" className={button('primary', 'sm')} onClick={() => void copyAll()}>
            <Icon name={copied ? 'check' : 'copy'} className="size-4" />
            {copied ? 'Đã chép' : 'Chép tất cả'}
          </button>
          <button type="button" className={button('secondary', 'sm')} onClick={() => void close()}>
            Đóng
          </button>
        </div>
      </div>
      <ul className="mt-4 grid gap-2 sm:grid-cols-2">
        {codes.map((c) => (
          <li key={c.id} className="flex items-center justify-between gap-3 rounded-sm bg-surface px-3 py-2">
            <span className="truncate text-ink">{c.name}</span>
            <span className="font-mono text-lg font-semibold tracking-wider text-ink">{c.accessCode}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
