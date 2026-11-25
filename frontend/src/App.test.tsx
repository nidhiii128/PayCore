import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { App, SESSION_EXPIRED } from './App'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

const wallet = {
  id: '11111111-1111-4111-8111-111111111111',
  accountId: 'acc-1',
  currency: 'INR',
  balance: 50000,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
}

const entry = {
  entryId: 'e-1',
  transactionId: 't-1',
  type: 'DEPOSIT',
  direction: 'CREDIT',
  amount: 50000,
  currency: 'INR',
  balanceAfter: 50000,
  description: 'Salary',
  createdAt: '2026-01-01T10:00:00Z',
}

/** A fake backend: answers by path, and records every request. */
function fakeServer(handlers: Record<string, (init: RequestInit) => Response>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = String(input).split('?')[0]
    const handler = handlers[`${init.method ?? 'GET'} ${path}`]
    return handler ? handler(init) : json(404, { status: 404, code: 'NOT_FOUND' })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function logIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('Email'), 'asha@example.com')
  await user.type(screen.getByLabelText('Password'), 'a-long-password')
  await user.click(screen.getByRole('button', { name: 'Log in' }))
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('logs in and shows wallets, balances and ledger entries', async () => {
    const fetchMock = fakeServer({
      'POST /api/v1/auth/login': () => json(200, { accessToken: 'jwt-1', userId: 'u', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /api/v1/accounts': () => json(200, [{ id: 'acc-1', name: 'Main', type: 'PERSONAL', createdAt: '' }]),
      'GET /api/v1/accounts/acc-1/wallets': () => json(200, [wallet]),
      [`GET /api/v1/wallets/${wallet.id}/transactions`]: () =>
        json(200, { content: [entry], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    })
    const user = userEvent.setup()
    render(<App />)

    await logIn(user)

    expect(await screen.findByText('Signed in as')).toBeInTheDocument()
    const balances = await screen.findAllByText('₹500.00')
    expect(balances.length).toBeGreaterThan(0)
    expect(await screen.findByText('Salary')).toBeInTheDocument()
    expect(screen.getByText('Deposit in')).toBeInTheDocument()

    // Every call after login carries the token from memory.
    const accountsCall = fetchMock.mock.calls.find(([url]) => url === '/api/v1/accounts')!
    const headers = accountsCall[1]!.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer jwt-1')
  })

  it('never stores the token in localStorage or sessionStorage', async () => {
    fakeServer({
      'POST /api/v1/auth/login': () => json(200, { accessToken: 'jwt-secret', userId: 'u', tokenType: 'Bearer', expiresIn: 3600 }),
      'GET /api/v1/accounts': () => json(200, []),
    })
    const user = userEvent.setup()
    render(<App />)

    await logIn(user)
    await screen.findByText(/You have no wallets yet/)

    const stored = [...Object.values(localStorage), ...Object.values(sessionStorage)].join(' ')
    expect(stored).not.toContain('jwt-secret')
  })

  it('sends the user back to the login page when the API answers 401', async () => {
    fakeServer({
      'POST /api/v1/auth/login': () => json(200, { accessToken: 'jwt-1', userId: 'u', tokenType: 'Bearer', expiresIn: 3600 }),
      // An expired token: Spring Security answers 401 with no body.
      'GET /api/v1/accounts': () => new Response(null, { status: 401 }),
    })
    const user = userEvent.setup()
    render(<App />)

    await logIn(user)

    expect(await screen.findByRole('status')).toHaveTextContent(SESSION_EXPIRED)
    expect(screen.getByRole('button', { name: 'Log in' })).toBeInTheDocument()
  })

  it('shows the API message for wrong credentials and focuses it', async () => {
    fakeServer({
      'POST /api/v1/auth/login': () =>
        new Response(
          JSON.stringify({ status: 401, title: 'Unauthorized', detail: 'Invalid email or password', code: 'INVALID_CREDENTIALS' }),
          { status: 401, headers: { 'Content-Type': 'application/problem+json' } },
        ),
    })
    const user = userEvent.setup()
    render(<App />)

    await logIn(user)

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The email or password is not correct.')
    expect(alert).toHaveFocus()
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('requires both login fields and marks empty ones aria-invalid', async () => {
    const fetchMock = fakeServer({})
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(screen.getByRole('alert')).toHaveFocus()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
