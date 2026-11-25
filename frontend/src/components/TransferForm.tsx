import { useEffect, useRef, useState, type FormEvent } from 'react'
import type { Api } from '../api/client'
import { ApiError, NetworkError, messageFor } from '../api/problem'
import type { Transaction, TransferRequest, Wallet } from '../api/types'
import { formatMoney, parseAmount } from '../money'
import { ErrorSummary, type FormError } from './ErrorSummary'
import { Field } from './Field'

type FieldName = 'fromWalletId' | 'toWalletId' | 'amount' | 'description'
type FieldErrors = Partial<Record<FieldName, string>>

const LABELS: Record<FieldName, string> = {
  fromWalletId: 'From wallet',
  toWalletId: 'To wallet ID',
  amount: 'Amount',
  description: 'Description',
}

/** Business errors from the API that are really about one field, so the message is shown next to it. */
const FIELD_FOR_CODE: Record<string, FieldName> = {
  INSUFFICIENT_FUNDS: 'amount',
  CURRENCY_MISMATCH: 'toWalletId',
  SAME_WALLET_TRANSFER: 'toWalletId',
  WALLET_NOT_FOUND: 'toWalletId',
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const MAX_DESCRIPTION = 500

interface Props {
  api: Pick<Api, 'transfer'>
  wallets: Wallet[]
  onTransferred: (transaction: Transaction) => void
  /** Injected in tests. Each new transfer gets a fresh key. */
  newIdempotencyKey?: () => string
}

export function TransferForm({ api, wallets, onTransferred, newIdempotencyKey = () => crypto.randomUUID() }: Props) {
  const [chosenFromWalletId, setFromWalletId] = useState(wallets[0]?.id ?? '')
  const [toWalletId, setToWalletId] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [errors, setErrors] = useState<FormError[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState<string | null>(null)

  // A ref, not state: it blocks a second submit at once, even before React re-renders the disabled button.
  const inFlight = useRef(false)
  // After a network failure we cannot know if the server got the request. Retrying the same transfer with the
  // same key is then safe: the server replays the first result instead of moving the money twice.
  const retry = useRef<{ key: string; fingerprint: string } | null>(null)
  const successRef = useRef<HTMLParagraphElement>(null)

  // Falls back to the first wallet if the chosen one is no longer in the list.
  const fromWallet = wallets.find((w) => w.id === chosenFromWalletId) ?? wallets[0]
  const fromWalletId = fromWallet?.id ?? ''

  useEffect(() => {
    if (success) {
      successRef.current?.focus()
    }
  }, [success])

  function showErrors(next: FieldErrors, formMessage?: string) {
    setFieldErrors(next)
    const list: FormError[] = []
    if (formMessage) {
      list.push({ message: formMessage })
    }
    for (const name of Object.keys(LABELS) as FieldName[]) {
      if (next[name]) {
        list.push({ message: next[name]!, fieldId: name })
      }
    }
    setErrors(list)
  }

  function validate(): { body: TransferRequest } | { errors: FieldErrors } {
    const next: FieldErrors = {}
    if (!fromWallet) {
      next.fromWalletId = 'Choose the wallet to send from.'
    }
    const to = toWalletId.trim()
    if (!to) {
      next.toWalletId = 'Enter the ID of the wallet to send to.'
    } else if (!UUID_PATTERN.test(to)) {
      next.toWalletId = 'A wallet ID looks like 3f2b6c1e-8a4d-4c2e-9b7a-1d2e3f4a5b6c.'
    }
    let minorUnits = 0
    if (fromWallet) {
      const parsed = parseAmount(amount, fromWallet.currency)
      if (parsed.ok) {
        minorUnits = parsed.minorUnits
      } else {
        next.amount = parsed.message
      }
    }
    if (description.length > MAX_DESCRIPTION) {
      next.description = `Description must be ${MAX_DESCRIPTION} characters or fewer.`
    }
    if (Object.keys(next).length > 0) {
      return { errors: next }
    }
    const body: TransferRequest = { fromWalletId, toWalletId: to, amount: minorUnits }
    if (description.trim()) {
      body.description = description.trim()
    }
    return { body }
  }

  function errorsFromApi(error: unknown): { fields: FieldErrors; message?: string } {
    if (error instanceof ApiError) {
      const { code, errors: serverFieldErrors } = error.problem
      if (code === 'VALIDATION_FAILED' && serverFieldErrors) {
        const fields: FieldErrors = {}
        const unknown: string[] = []
        for (const [name, message] of Object.entries(serverFieldErrors)) {
          if (name in LABELS) {
            fields[name as FieldName] = `${LABELS[name as FieldName]} ${message}.`
          } else {
            unknown.push(`${name} ${message}.`)
          }
        }
        return { fields, message: unknown.length > 0 ? unknown.join(' ') : undefined }
      }
      if (code && FIELD_FOR_CODE[code]) {
        return { fields: { [FIELD_FOR_CODE[code]]: messageFor(error) } }
      }
    }
    if (error instanceof NetworkError) {
      return {
        fields: {},
        message: `${error.message} Sending the same transfer again is safe: it reuses the same request ID, so the money moves at most once.`,
      }
    }
    return { fields: {}, message: messageFor(error) }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (inFlight.current) {
      return
    }
    setSuccess(null)
    const result = validate()
    if ('errors' in result) {
      showErrors(result.errors)
      return
    }

    const fingerprint = JSON.stringify(result.body)
    const key = retry.current?.fingerprint === fingerprint ? retry.current.key : newIdempotencyKey()
    retry.current = { key, fingerprint }

    inFlight.current = true
    setSubmitting(true)
    showErrors({})
    try {
      const transaction = await api.transfer(result.body, key)
      retry.current = null
      setToWalletId('')
      setAmount('')
      setDescription('')
      setSuccess(`Sent ${formatMoney(transaction.amount, transaction.currency)}. Transaction ID: ${transaction.id}`)
      onTransferred(transaction)
    } catch (error) {
      // Any answer from the server settles this key (success is stored, a failure is rolled back),
      // so only a network failure keeps it for a retry.
      if (!(error instanceof NetworkError)) {
        retry.current = null
      }
      const { fields, message } = errorsFromApi(error)
      showErrors(fields, message)
    } finally {
      inFlight.current = false
      setSubmitting(false)
    }
  }

  if (wallets.length === 0) {
    return <p>You need a wallet before you can send money.</p>
  }

  return (
    <form onSubmit={handleSubmit} noValidate aria-labelledby="send-money-title">
      <ErrorSummary title="The transfer was not sent" errors={errors} />
      {success && (
        <p ref={successRef} className="success" role="status" tabIndex={-1}>
          {success}
        </p>
      )}

      <div className={fieldErrors.fromWalletId ? 'field field-error' : 'field'}>
        <label htmlFor="fromWalletId">{LABELS.fromWalletId}</label>
        {fieldErrors.fromWalletId && (
          <p id="fromWalletId-error" className="error-text">
            {fieldErrors.fromWalletId}
          </p>
        )}
        <select
          id="fromWalletId"
          name="fromWalletId"
          value={fromWalletId}
          onChange={(e) => setFromWalletId(e.target.value)}
          aria-invalid={fieldErrors.fromWalletId ? true : undefined}
          aria-describedby={fieldErrors.fromWalletId ? 'fromWalletId-error' : undefined}
        >
          {wallets.map((w) => (
            <option key={w.id} value={w.id}>
              {w.currency} wallet, balance {formatMoney(w.balance, w.currency)}
            </option>
          ))}
        </select>
      </div>

      <Field
        id="toWalletId"
        label={LABELS.toWalletId}
        hint="Paste the ID of the wallet that receives the money."
        autoComplete="off"
        spellCheck={false}
        value={toWalletId}
        onChange={(e) => setToWalletId(e.target.value)}
        error={fieldErrors.toWalletId}
      />
      <Field
        id="amount"
        label={fromWallet ? `Amount in ${fromWallet.currency}` : LABELS.amount}
        hint="For example 12.50"
        inputMode="decimal"
        autoComplete="off"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        error={fieldErrors.amount}
      />
      <Field
        id="description"
        label={`${LABELS.description} (optional)`}
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        error={fieldErrors.description}
      />

      <button type="submit" disabled={submitting}>
        {submitting ? 'Sending…' : 'Send money'}
      </button>
    </form>
  )
}
