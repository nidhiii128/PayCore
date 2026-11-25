import { randomUUID } from 'node:crypto'

// Test data is created straight through the REST API, the same way any client would.
// Each test makes its own user, so tests never share wallets and can run in any order.

export const API_URL = process.env.PAYCORE_API_URL ?? 'http://localhost:8080'

export const PASSWORD = 'e2e-test-password'

export interface TestUser {
  email: string
  token: string
}

export interface TestWallet {
  id: string
  currency: string
}

async function call<T>(method: string, path: string, token?: string, body?: unknown, idempotencyKey?: string) {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
  const response = await fetch(API_URL + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    throw new Error(`${method} ${path} failed with ${response.status}: ${await response.text()}`)
  }
  return (await response.json()) as T
}

export async function registerUser(): Promise<TestUser> {
  const email = `e2e-${randomUUID()}@example.com`
  const auth = await call<{ accessToken: string }>('POST', '/api/v1/auth/register', undefined, {
    email,
    password: PASSWORD,
    fullName: 'E2E User',
  })
  return { email, token: auth.accessToken }
}

/** Opens an account with one wallet. A deposit of `deposit` minor units is made if it is above zero. */
export async function createWallet(user: TestUser, accountName: string, currency: string, deposit = 0) {
  const account = await call<{ id: string }>('POST', '/api/v1/accounts', user.token, { name: accountName, type: 'PERSONAL' })
  const wallet = await call<TestWallet>('POST', `/api/v1/accounts/${account.id}/wallets`, user.token, { currency })
  if (deposit > 0) {
    await call('POST', `/api/v1/wallets/${wallet.id}/deposits`, user.token, { amount: deposit, description: 'Opening deposit' }, randomUUID())
  }
  return wallet
}

export async function balanceOf(user: TestUser, walletId: string): Promise<number> {
  return (await call<{ balance: number }>('GET', `/api/v1/wallets/${walletId}`, user.token)).balance
}

export async function entriesOf(user: TestUser, walletId: string) {
  const page = await call<{ content: Array<{ type: string; direction: string; amount: number }> }>(
    'GET',
    `/api/v1/wallets/${walletId}/transactions?size=100`,
    user.token,
  )
  return page.content
}
