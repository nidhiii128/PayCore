import { expect, test, type Page } from '@playwright/test'
import { PASSWORD, balanceOf, createWallet, entriesOf, registerUser, type TestUser, type TestWallet } from './support/api.ts'

let user: TestUser
let main: TestWallet
let savings: TestWallet

test.beforeEach(async () => {
  user = await registerUser()
  main = await createWallet(user, 'Main', 'INR', 50_000) // ₹500.00
  savings = await createWallet(user, 'Savings', 'INR')
})

async function logIn(page: Page) {
  await page.goto('/')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page.getByRole('heading', { name: 'Your wallets' })).toBeVisible()
}

function walletRow(page: Page, wallet: TestWallet) {
  return page.getByTestId(`wallet-${wallet.id}`)
}

test('logs in and shows wallets, balances and recent ledger entries', async ({ page }) => {
  await logIn(page)

  await expect(walletRow(page, main).getByTestId('balance')).toHaveText('₹500.00')
  await expect(walletRow(page, savings).getByTestId('balance')).toHaveText('₹0.00')

  const ledger = page.getByRole('table', { name: /Latest entries for the INR wallet in Main/ })
  await expect(ledger.getByRole('row', { name: /Deposit in Opening deposit \+₹500\.00 ₹500\.00/ })).toBeVisible()
})

test('sends a transfer using only the keyboard and updates balances and the ledger', async ({ page }) => {
  await logIn(page)

  await page.getByLabel('To wallet ID').focus()
  await page.keyboard.type(savings.id)
  await page.keyboard.press('Tab')
  await page.keyboard.type('120.25')
  await page.keyboard.press('Tab')
  await page.keyboard.type('Rent share')
  await page.keyboard.press('Enter')

  const status = page.getByRole('status')
  await expect(status).toContainText('Sent ₹120.25')
  await expect(status).toBeFocused()

  await expect(walletRow(page, main).getByTestId('balance')).toHaveText('₹379.75')
  await expect(walletRow(page, savings).getByTestId('balance')).toHaveText('₹120.25')
  await expect(page.getByRole('row', { name: /Transfer out Rent share -₹120\.25 ₹379\.75/ })).toBeVisible()

  expect(await balanceOf(user, main.id)).toBe(37_975)
})

test('a transfer larger than the balance shows the API error and moves no money', async ({ page }) => {
  await logIn(page)

  await page.getByLabel('To wallet ID').fill(savings.id)
  await page.getByLabel('Amount in INR').fill('900')
  await page.getByRole('button', { name: 'Send money' }).click()

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('This wallet does not have enough money for that transfer.')
  await expect(alert).toBeFocused()
  await expect(page.getByLabel('Amount in INR')).toHaveAttribute('aria-invalid', 'true')

  expect(await balanceOf(user, main.id)).toBe(50_000)
  expect(await balanceOf(user, savings.id)).toBe(0)
})

test('an unknown destination wallet shows the API error next to the field', async ({ page }) => {
  await logIn(page)

  await page.getByLabel('To wallet ID').fill('00000000-0000-4000-8000-000000000000')
  await page.getByLabel('Amount in INR').fill('10')
  await page.getByRole('button', { name: 'Send money' }).click()

  await expect(page.getByRole('alert')).toContainText('No wallet was found with that ID.')
  await expect(page.getByLabel('To wallet ID')).toHaveAttribute('aria-invalid', 'true')
  expect(await balanceOf(user, main.id)).toBe(50_000)
})

test('double clicking Send money creates exactly one transfer', async ({ page }) => {
  await logIn(page)

  // Hold each transfer request for a moment before passing it on to the real backend, so the second click
  // surely lands while the first request is still in flight.
  const transferRequests: string[] = []
  await page.route('**/api/v1/transfers', async (route) => {
    transferRequests.push(route.request().headers()['idempotency-key'])
    await new Promise((resolve) => setTimeout(resolve, 500))
    await route.continue()
  })

  await page.getByLabel('To wallet ID').fill(savings.id)
  await page.getByLabel('Amount in INR').fill('50')
  await page.getByRole('button', { name: 'Send money' }).dblclick()

  await expect(page.getByRole('button', { name: 'Sending…' })).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('Sent ₹50.00')

  expect(transferRequests).toHaveLength(1)
  expect(transferRequests[0]).toMatch(/^[0-9a-f-]{36}$/)
  expect(await balanceOf(user, main.id)).toBe(45_000)
  const transfers = (await entriesOf(user, main.id)).filter((e) => e.type === 'TRANSFER')
  expect(transfers).toHaveLength(1)
})

test('wrong password shows the API error on the login page', async ({ page }) => {
  await page.goto('/')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Log in' }).click()

  const alert = page.getByRole('alert')
  await expect(alert).toContainText('The email or password is not correct.')
  await expect(alert).toBeFocused()
})

test('a token the backend rejects sends the user back to the login page', async ({ page }) => {
  await logIn(page)
  await expect(walletRow(page, savings)).toBeVisible()

  // Tamper with the token on every following request. The real backend answers 401.
  await page.route('**/api/v1/**', (route) =>
    route.continue({ headers: { ...route.request().headers(), authorization: 'Bearer tampered.token.value' } }),
  )
  await page.getByRole('button', { name: /Show entries for INR wallet in Savings/ }).click()

  await expect(page.getByRole('status')).toHaveText('Your session has ended. Please log in again.')
  await expect(page.getByRole('button', { name: 'Log in' })).toBeVisible()
})
