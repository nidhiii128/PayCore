// Creates a demo user with two INR wallets and a deposit, so there is something to see in the UI.
// Usage: npm run seed   (the backend must be running, PAYCORE_API_URL defaults to http://localhost:8080)
// Running it again reuses the same user and adds nothing new.
import { randomUUID } from 'node:crypto'

const API_URL = process.env.PAYCORE_API_URL ?? 'http://localhost:8080'
// Local demo values only. Never use these on a real deployment.
const email = process.env.DEMO_EMAIL ?? 'demo@example.com'
const password = process.env.DEMO_PASSWORD ?? 'demo-password'

async function call(method, path, { token, body, idempotencyKey } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey
  const response = await fetch(API_URL + path, { method, headers, body: body && JSON.stringify(body) })
  const text = await response.text()
  const json = text ? JSON.parse(text) : null
  return { ok: response.ok, status: response.status, json }
}

let auth = await call('POST', '/api/v1/auth/register', { body: { email, password, fullName: 'Demo User' } })
if (auth.json?.code === 'EMAIL_ALREADY_REGISTERED') {
  auth = await call('POST', '/api/v1/auth/login', { body: { email, password } })
}
if (!auth.ok) {
  console.error('Could not register or log in the demo user:', auth.status, auth.json)
  process.exit(1)
}
const token = auth.json.accessToken

const accounts = await call('GET', '/api/v1/accounts', { token })
if (accounts.json.length === 0) {
  for (const [name, deposit] of [['Main', 100_000], ['Savings', 0]]) {
    const account = await call('POST', '/api/v1/accounts', { token, body: { name, type: 'PERSONAL' } })
    const wallet = await call('POST', `/api/v1/accounts/${account.json.id}/wallets`, { token, body: { currency: 'INR' } })
    if (deposit > 0) {
      await call('POST', `/api/v1/wallets/${wallet.json.id}/deposits`, {
        token,
        body: { amount: deposit, description: 'Opening deposit' },
        idempotencyKey: randomUUID(),
      })
    }
  }
  console.log('Created accounts Main (₹1,000.00) and Savings (₹0.00).')
} else {
  console.log('The demo user already has accounts. Nothing new was created.')
}
console.log(`Log in with email ${email} and password ${password}`)
