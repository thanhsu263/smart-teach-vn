import { useCallback, useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { ConfirmContext, type ConfirmOptions } from './confirm-context'
import { button } from './ui'

interface Request {
  options: ConfirmOptions
  resolve: (ok: boolean) => void
}

/**
 * One <dialog> for the whole app. The browser's modal dialog already traps focus, closes on Escape
 * and returns focus to the button that opened it, so no extra library is needed.
 */
export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [request, setRequest] = useState<Request | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  const confirm = useCallback(
    (options: ConfirmOptions) => new Promise<boolean>((resolve) => setRequest({ options, resolve })),
    [],
  )

  useEffect(() => {
    const dialog = dialogRef.current
    if (request && dialog && !dialog.open) {
      // Escape closes without a value, so clear the answer left over from the previous question.
      dialog.returnValue = ''
      dialog.showModal()
    }
  }, [request])

  const onClose = () => {
    request?.resolve(dialogRef.current?.returnValue === 'confirm')
    setRequest(null)
  }

  const options = request?.options
  return (
    <ConfirmContext value={confirm}>
      {children}
      <dialog
        ref={dialogRef}
        aria-labelledby={titleId}
        onClose={onClose}
        className="m-auto w-[calc(100%-2rem)] max-w-md rounded-lg bg-surface p-6 text-ink shadow-raised"
      >
        {options && (
          <form method="dialog" className="space-y-5">
            <div className="space-y-2">
              <h2 id={titleId} className="text-heading">
                {options.title}
              </h2>
              {options.message && <p className="text-ink-muted">{options.message}</p>}
            </div>
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              {/* showModal() focuses the first button: Cancel, so Enter pressed by habit deletes nothing. */}
              <button value="cancel" className={button('secondary')}>
                Huỷ
              </button>
              <button value="confirm" className={button(options.danger ? 'danger' : 'primary')}>
                {options.confirmLabel ?? 'Đồng ý'}
              </button>
            </div>
          </form>
        )}
      </dialog>
    </ConfirmContext>
  )
}
