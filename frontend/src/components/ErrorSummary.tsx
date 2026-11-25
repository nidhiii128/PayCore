import { useEffect, useRef } from 'react'

export interface FormError {
  message: string
  /** id of the input this error belongs to. Errors without one are about the whole request. */
  fieldId?: string
}

interface Props {
  title?: string
  errors: FormError[]
}

/**
 * Lists a form's errors at the top of the form and takes keyboard focus whenever a new list of errors arrives,
 * so screen reader and keyboard users learn that the submit failed. Each field error links to its input.
 */
export function ErrorSummary({ title = 'There is a problem', errors }: Props) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (errors.length > 0) {
      ref.current?.focus()
    }
  }, [errors])

  if (errors.length === 0) {
    return null
  }

  return (
    <div ref={ref} className="error-summary" role="alert" tabIndex={-1} aria-labelledby="error-summary-title">
      <h3 id="error-summary-title">{title}</h3>
      <ul>
        {errors.map((error) => (
          <li key={`${error.fieldId ?? 'form'}:${error.message}`}>
            {error.fieldId ? (
              <a
                href={`#${error.fieldId}`}
                onClick={(event) => {
                  event.preventDefault()
                  document.getElementById(error.fieldId!)?.focus()
                }}
              >
                {error.message}
              </a>
            ) : (
              error.message
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}
