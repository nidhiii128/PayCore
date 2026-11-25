import { ApiError, NetworkError, readProblem } from './problem'
import type { Account, AuthResponse, Page, Transaction, TransferRequest, Wallet, WalletEntry } from './types'

export interface ApiOptions {
  /** Returns the current access token, or null when logged out. */
  getToken: () => string | null
  /** Called when the server rejects our token (401), so the app can send the user back to the login page. */
  onUnauthorized: () => void
  /** Injected in tests. Defaults to the browser's fetch. */
  fetchFn?: typeof fetch
  /** Paths are relative to this. In the browser it stays "", so calls go to the same origin and Vite proxies them. */
  baseUrl?: string
}

export type Api = ReturnType<typeof createApi>

export function createApi({ getToken, onUnauthorized, fetchFn, baseUrl = '' }: ApiOptions) {
  // Look up fetch at call time, so a test can replace globalThis.fetch after the client is created.
  const doFetch: typeof fetch = fetchFn ?? ((input, init) => globalThis.fetch(input, init))

  async function request<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}) {
    const token = getToken()
    const allHeaders: Record<string, string> = { Accept: 'application/json, application/problem+json', ...headers }
    if (body !== undefined) {
      allHeaders['Content-Type'] = 'application/json'
    }
    if (token) {
      allHeaders.Authorization = `Bearer ${token}`
    }

    let response: Response
    try {
      response = await doFetch(baseUrl + path, {
        method,
        headers: allHeaders,
        body: body === undefined ? undefined : JSON.stringify(body),
      })
    } catch {
      throw new NetworkError()
    }

    if (!response.ok) {
      const problem = await readProblem(response)
      // A 401 on a call that sent a token means the token expired or is invalid. A 401 from the login
      // endpoint is just a wrong password, and the login form shows it.
      if (response.status === 401 && token) {
        onUnauthorized()
      }
      throw new ApiError(problem)
    }
    return (await response.json()) as T
  }

  return {
    login: (email: string, password: string) =>
      request<AuthResponse>('POST', '/api/v1/auth/login', { email, password }),

    listAccounts: () => request<Account[]>('GET', '/api/v1/accounts'),

    listWallets: (accountId: string) =>
      request<Wallet[]>('GET', `/api/v1/accounts/${encodeURIComponent(accountId)}/wallets`),

    walletEntries: (walletId: string, size = 20) =>
      request<Page<WalletEntry>>('GET', `/api/v1/wallets/${encodeURIComponent(walletId)}/transactions?size=${size}`),

    /** The caller chooses the key, so a retry of the same transfer can reuse it. */
    transfer: (body: TransferRequest, idempotencyKey: string) =>
      request<Transaction>('POST', '/api/v1/transfers', body, { 'Idempotency-Key': idempotencyKey }),
  }
}
