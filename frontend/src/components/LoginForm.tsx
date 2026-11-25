import { useState, type FormEvent } from 'react'
import type { Api } from '../api/client'
import { messageFor } from '../api/problem'
import { ErrorSummary, type FormError } from './ErrorSummary'
import { Field } from './Field'

interface Props {
  api: Pick<Api, 'login'>
  onLogin: (token: string, email: string) => void
  /** Shown above the form, for example after the session expired. */
  notice?: string
}

export function LoginForm({ api, onLogin, notice }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<{ email?: string; password?: string }>({})
  const [errors, setErrors] = useState<FormError[]>([])
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (submitting) {
      return
    }
    const nextFieldErrors: { email?: string; password?: string } = {}
    if (!email.trim()) nextFieldErrors.email = 'Enter your email.'
    if (!password) nextFieldErrors.password = 'Enter your password.'
    setFieldErrors(nextFieldErrors)
    if (nextFieldErrors.email || nextFieldErrors.password) {
      setErrors([
        ...(nextFieldErrors.email ? [{ message: nextFieldErrors.email, fieldId: 'email' }] : []),
        ...(nextFieldErrors.password ? [{ message: nextFieldErrors.password, fieldId: 'password' }] : []),
      ])
      return
    }

    setSubmitting(true)
    try {
      const response = await api.login(email.trim(), password)
      onLogin(response.accessToken, email.trim())
    } catch (error) {
      setPassword('')
      setErrors([{ message: messageFor(error) }])
      setSubmitting(false)
    }
  }

  return (
    <main className="card narrow">
      <h1>PayCore</h1>
      <h2>Log in</h2>
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}
      <form onSubmit={handleSubmit} noValidate>
        <ErrorSummary errors={errors} />
        <Field
          id="email"
          label="Email"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
        />
        <Field
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <button type="submit" disabled={submitting}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>
    </main>
  )
}
