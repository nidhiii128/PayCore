import type { InputHTMLAttributes, ReactNode } from 'react'

interface Props extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> {
  id: string
  label: string
  hint?: ReactNode
  error?: string
}

/** A labelled text input. When it has an error it sets aria-invalid and links the message with aria-describedby. */
export function Field({ id, label, hint, error, ...inputProps }: Props) {
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={error ? 'field field-error' : 'field'}>
      <label htmlFor={id}>{label}</label>
      {hint && (
        <p id={hintId} className="hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="error-text">
          {error}
        </p>
      )}
      <input id={id} name={id} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...inputProps} />
    </div>
  )
}
