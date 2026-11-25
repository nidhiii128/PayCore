import { describe, expect, it, vi } from 'vitest'
import { createApi } from './client'
import { ApiError, NetworkError, messageFor } from './problem'

function problemResponse(status: number, body: object) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/problem+json' } })
}

function setup(token: string | null, response: Response | Error) {
  const fetchFn = vi.fn<typeof fetch>(() =>
    response instanceof Error ? Promise.reject(response) : Promise.resolve(response),
  )
  const onUnauthorized = vi.fn()
  const api = createApi({ getToken: () => token, onUnauthorized, fetchFn })
  return { api, fetchFn, onUnauthorized }
}

describe('createApi', () => {
  it('sends the bearer token and the Idempotency-Key header on a transfer', async () => {
    const { api, fetchFn } = setup('jwt-123', new Response(JSON.stringify({ id: 'tx-1' }), { status: 201 }))

    await api.transfer({ fromWalletId: 'a', toWalletId: 'b', amount: 100 }, 'key-1')

    const [url, init] = fetchFn.mock.calls[0]
    expect(url).toBe('/api/v1/transfers')
    expect(init?.method).toBe('POST')
    expect(init?.headers).toMatchObject({
      Authorization: 'Bearer jwt-123',
      'Idempotency-Key': 'key-1',
      'Content-Type': 'application/json',
    })
    expect(JSON.parse(init?.body as string)).toEqual({ fromWalletId: 'a', toWalletId: 'b', amount: 100 })
  })

  it('turns an RFC 9457 problem into an ApiError with its code and field errors', async () => {
    const { api } = setup(
      'jwt',
      problemResponse(400, {
        title: 'Bad Request',
        status: 400,
        detail: 'Request validation failed',
        code: 'VALIDATION_FAILED',
        errors: { amount: 'must be greater than 0' },
      }),
    )

    const error = await api.transfer({ fromWalletId: 'a', toWalletId: 'b', amount: 0 }, 'k').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.problem.errors).toEqual({ amount: 'must be greater than 0' })
  })

  it('calls onUnauthorized when the server rejects the token', async () => {
    // Spring Security answers an expired or invalid token with 401 and an empty body.
    const { api, onUnauthorized } = setup('expired-jwt', new Response(null, { status: 401 }))

    await expect(api.listAccounts()).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('does not log the user out when the login itself fails with 401', async () => {
    const { api, onUnauthorized } = setup(
      null,
      problemResponse(401, { status: 401, detail: 'Invalid email or password', code: 'INVALID_CREDENTIALS' }),
    )

    const error = await api.login('a@example.com', 'wrong').catch((e) => e)

    expect(messageFor(error)).toBe('The email or password is not correct.')
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('reports a failed connection as a NetworkError', async () => {
    const { api } = setup('jwt', new TypeError('Failed to fetch'))

    await expect(api.listAccounts()).rejects.toBeInstanceOf(NetworkError)
  })

  it('copes with error bodies that are not JSON', async () => {
    const { api } = setup('jwt', new Response('<html>Bad gateway</html>', { status: 502, statusText: 'Bad Gateway' }))

    const error = await api.listAccounts().catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(502)
    expect(messageFor(error)).toBe('Something went wrong on the server. Please try again.')
  })
})
