/**
 * The backend sends every error as an RFC 9457 problem document, for example:
 * { "status": 422, "title": "Unprocessable Entity", "detail": "...", "code": "INSUFFICIENT_FUNDS" }
 * Validation errors also carry "errors": { fieldName: message }.
 */
export interface Problem {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  code?: string
  errors?: Record<string, string>
}

/** Thrown for any non-2xx response. A network failure (no response at all) is a NetworkError instead. */
export class ApiError extends Error {
  readonly problem: Problem

  constructor(problem: Problem) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = 'ApiError'
    this.problem = problem
  }

  get status(): number {
    return this.problem.status
  }

  get code(): string | undefined {
    return this.problem.code
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Could not reach the PayCore server. Check your connection and try again.')
    this.name = 'NetworkError'
  }
}

/** Builds a Problem from any response. Falls back to the status line when the body is not a problem document. */
export async function readProblem(response: Response): Promise<Problem> {
  const fallback: Problem = { status: response.status, title: response.statusText || undefined }
  const text = await response.text().catch(() => '')
  if (!text) {
    return fallback
  }
  try {
    const parsed: unknown = JSON.parse(text)
    if (parsed && typeof parsed === 'object') {
      return { ...fallback, ...(parsed as Partial<Problem>), status: response.status }
    }
  } catch {
    // Not JSON, for example an HTML error page from a proxy.
  }
  return fallback
}

/** Plain language messages for the error codes a user can actually hit in this UI. */
const MESSAGES: Record<string, string> = {
  INSUFFICIENT_FUNDS: 'This wallet does not have enough money for that transfer.',
  CURRENCY_MISMATCH: 'Both wallets must use the same currency.',
  SAME_WALLET_TRANSFER: 'You cannot send money to the same wallet it comes from.',
  WALLET_NOT_FOUND: 'No wallet was found with that ID.',
  WALLET_FROZEN: 'One of the wallets is frozen, so no money can move in or out of it.',
  IDEMPOTENCY_KEY_REUSED: 'This request was already sent with different details. Please submit the form again.',
  CONCURRENT_MODIFICATION: 'The wallet changed while your transfer was running. Please try again.',
  INVALID_CREDENTIALS: 'The email or password is not correct.',
  RATE_LIMITED: 'Too many requests. Please wait a minute and try again.',
}

/** The message to show for a whole request, as opposed to one form field. */
export function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    const { code, detail, status } = error.problem
    if (code && MESSAGES[code]) {
      return MESSAGES[code]
    }
    if (code === 'VALIDATION_FAILED') {
      return 'Some fields need fixing.'
    }
    if (status >= 500) {
      return 'Something went wrong on the server. Please try again.'
    }
    return detail ?? `Request failed with status ${status}.`
  }
  if (error instanceof NetworkError) {
    return error.message
  }
  return 'Something unexpected went wrong. Please try again.'
}
