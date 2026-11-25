import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError, NetworkError } from '../api/problem'
import type { Transaction, TransferRequest, Wallet } from '../api/types'
import { TransferForm } from './TransferForm'

const FROM = '11111111-1111-4111-8111-111111111111'
const TO = '22222222-2222-4222-8222-222222222222'

const wallets: Wallet[] = [
  {
    id: FROM,
    accountId: 'acc',
    currency: 'INR',
    balance: 50000,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  },
]

function transaction(amount: number): Transaction {
  return {
    id: 'tx-1',
    type: 'TRANSFER',
    status: 'COMPLETED',
    amount,
    currency: 'INR',
    fromWalletId: FROM,
    toWalletId: TO,
    createdAt: '2026-01-01T00:00:00Z',
  }
}

/** A promise the test resolves or rejects by hand, to hold a request "in flight". */
function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function renderForm(transfer: (body: TransferRequest, key: string) => Promise<Transaction>) {
  const api = { transfer: vi.fn(transfer) }
  const onTransferred = vi.fn()
  let n = 0
  const newIdempotencyKey = vi.fn(() => `key-${++n}`)
  render(<TransferForm api={api} wallets={wallets} onTransferred={onTransferred} newIdempotencyKey={newIdempotencyKey} />)
  return { api, onTransferred, user: userEvent.setup() }
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>, amount = '12.50') {
  await user.type(screen.getByLabelText('To wallet ID'), TO)
  await user.type(screen.getByLabelText('Amount in INR'), amount)
}

describe('TransferForm', () => {
  it('has a label for every field', () => {
    renderForm(() => Promise.resolve(transaction(1)))

    expect(screen.getByLabelText('From wallet')).toBeInTheDocument()
    expect(screen.getByLabelText('To wallet ID')).toBeInTheDocument()
    expect(screen.getByLabelText('Amount in INR')).toBeInTheDocument()
    expect(screen.getByLabelText('Description (optional)')).toBeInTheDocument()
  })

  it('sends the amount in minor units with an Idempotency-Key and shows the result', async () => {
    const { api, onTransferred, user } = renderForm(() => Promise.resolve(transaction(1250)))
    await fillValidForm(user)

    await user.click(screen.getByRole('button', { name: 'Send money' }))

    expect(api.transfer).toHaveBeenCalledWith({ fromWalletId: FROM, toWalletId: TO, amount: 1250 }, 'key-1')
    expect(await screen.findByRole('status')).toHaveTextContent('Sent ₹12.50. Transaction ID: tx-1')
    expect(screen.getByRole('status')).toHaveFocus()
    expect(onTransferred).toHaveBeenCalledOnce()
  })

  it('disables the button while the request runs, so a double click sends one transfer', async () => {
    const pending = deferred<Transaction>()
    const { api, user } = renderForm(() => pending.promise)
    await fillValidForm(user)
    const button = screen.getByRole('button', { name: 'Send money' })

    await user.dblClick(button)

    expect(api.transfer).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled()

    pending.resolve(transaction(1250))
    expect(await screen.findByRole('button', { name: 'Send money' })).toBeEnabled()
    expect(api.transfer).toHaveBeenCalledTimes(1)
  })

  it('also ignores a second submit made with the Enter key before React re-renders', async () => {
    const pending = deferred<Transaction>()
    const { api, user } = renderForm(() => pending.promise)
    await fillValidForm(user)

    await user.keyboard('{Enter}{Enter}')

    expect(api.transfer).toHaveBeenCalledTimes(1)
    pending.resolve(transaction(1250))
    await screen.findByRole('status')
  })

  it('validates in the browser, marks bad fields aria-invalid and moves focus to the error summary', async () => {
    const { api, user } = renderForm(() => Promise.resolve(transaction(1)))
    await user.type(screen.getByLabelText('To wallet ID'), 'not-a-uuid')
    await user.type(screen.getByLabelText('Amount in INR'), '1.234')

    await user.click(screen.getByRole('button', { name: 'Send money' }))

    const summary = screen.getByRole('alert')
    expect(summary).toHaveFocus()
    expect(summary).toHaveTextContent('INR amounts can have at most 2 decimal places.')
    expect(screen.getByLabelText('To wallet ID')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Amount in INR')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('Amount in INR')).toHaveAccessibleDescription(
      'For example 12.50 INR amounts can have at most 2 decimal places.',
    )
    expect(screen.getByLabelText('From wallet')).not.toHaveAttribute('aria-invalid')
    expect(api.transfer).not.toHaveBeenCalled()
  })

  it('links each error in the summary to its field', async () => {
    const { user } = renderForm(() => Promise.resolve(transaction(1)))
    await user.click(screen.getByRole('button', { name: 'Send money' }))

    await user.click(screen.getByRole('link', { name: 'Enter an amount.' }))

    expect(screen.getByLabelText('Amount in INR')).toHaveFocus()
  })

  it('shows insufficient funds from the API next to the amount', async () => {
    const { user } = renderForm(() =>
      Promise.reject(
        new ApiError({
          status: 422,
          title: 'Unprocessable Entity',
          detail: `Insufficient funds in wallet ${FROM}`,
          code: 'INSUFFICIENT_FUNDS',
        }),
      ),
    )
    await fillValidForm(user, '1000')

    await user.click(screen.getByRole('button', { name: 'Send money' }))

    const summary = await screen.findByRole('alert')
    expect(summary).toHaveTextContent('This wallet does not have enough money for that transfer.')
    expect(summary).toHaveFocus()
    expect(screen.getByLabelText('Amount in INR')).toHaveAttribute('aria-invalid', 'true')
  })

  it('maps server validation errors to the matching fields', async () => {
    const { user } = renderForm(() =>
      Promise.reject(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          detail: 'Request validation failed',
          errors: { description: 'size must be between 0 and 500' },
        }),
      ),
    )
    await fillValidForm(user)

    await user.click(screen.getByRole('button', { name: 'Send money' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Description size must be between 0 and 500.')
    expect(screen.getByLabelText('Description (optional)')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows a general error that is not about one field, such as a frozen wallet', async () => {
    const { user } = renderForm(() =>
      Promise.reject(new ApiError({ status: 422, code: 'WALLET_FROZEN', detail: 'Wallet x is frozen' })),
    )
    await fillValidForm(user)

    await user.click(screen.getByRole('button', { name: 'Send money' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'One of the wallets is frozen, so no money can move in or out of it.',
    )
  })

  it('reuses the Idempotency-Key when retrying after a network failure, and uses a new one after an answer', async () => {
    const results: Array<() => Promise<Transaction>> = [
      () => Promise.reject(new NetworkError()),
      () => Promise.resolve(transaction(1250)),
      () => Promise.reject(new ApiError({ status: 422, code: 'INSUFFICIENT_FUNDS' })),
      () => Promise.reject(new ApiError({ status: 422, code: 'INSUFFICIENT_FUNDS' })),
    ]
    const { api, user } = renderForm(() => results.shift()!())
    const send = () => user.click(screen.getByRole('button', { name: 'Send money' }))

    await fillValidForm(user)
    await send()
    expect(await screen.findByRole('alert')).toHaveTextContent('Sending the same transfer again is safe')
    await send()
    await screen.findByRole('status')

    await fillValidForm(user, '99')
    await send()
    await screen.findByRole('alert')
    await send()
    await waitFor(() => expect(api.transfer).toHaveBeenCalledTimes(4))

    const keys = api.transfer.mock.calls.map(([, key]) => key)
    expect(keys).toEqual(['key-1', 'key-1', 'key-2', 'key-3'])
  })
})
