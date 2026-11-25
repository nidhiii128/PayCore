// Load test: many concurrent transfers between a pool of funded wallets, then a check that no money was created or
// destroyed. Rate limiting allows 300 requests/minute per user, so throughput scales with USERS; turn it off
// (RATE_LIMIT_ENABLED=false) to measure the payment path alone.
//
//   k6 run -e BASE_URL=https://<app>.azurecontainerapps.io loadtest/transfers.js
//   k6 run -e BASE_URL=http://localhost:8080 -e USERS=50 -e VUS=100 -e DURATION=2m loadtest/transfers.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 20);
const STARTING_BALANCE = 1_000_000; // ₹10,000.00 in paise
const JSON_HEADERS = { 'Content-Type': 'application/json' };

const moneyLost = new Counter('money_conservation_violations');
const rateLimited = new Counter('rate_limited');

export const options = {
  setupTimeout: '10m',
  scenarios: {
    transfers: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '1m',
    },
  },
  thresholds: {
    'http_req_duration{name:transfer}': ['p(95)<500', 'p(99)<1000'],
    'checks{name:transfer}': ['rate>0.99'],
    money_conservation_violations: ['count==0'],
  },
};

// Setup and teardown must not be cut short by the rate limiter (30/minute anonymous, 300/minute per user, and the
// load phase uses up each user's window), so they back off and retry on 429.
function request(method, path, body, headers) {
  for (let attempt = 0; attempt < 30; attempt++) {
    const res = http.request(method, `${BASE_URL}${path}`, body === null ? null : JSON.stringify(body),
        { headers: { ...JSON_HEADERS, ...headers } });
    if (res.status !== 429) return res;
    sleep(Number(res.headers['Retry-After'] || 5));
  }
  throw new Error(`${method} ${path} still rate limited after 30 attempts`);
}

const post = (path, body, headers) => request('POST', path, body, headers);

function mustSucceed(res, what) {
  if (res.status < 200 || res.status >= 300) throw new Error(`${what} failed: ${res.status} ${res.body}`);
  return res.json();
}

export function setup() {
  const run = Date.now();
  const users = [];
  for (let i = 0; i < USERS; i++) {
    const { accessToken } = mustSucceed(post('/api/v1/auth/register', {
      email: `load-${run}-${i}@example.com`, password: 'load-test-password', fullName: `Load ${i}`,
    }), 'register');
    const auth = { Authorization: `Bearer ${accessToken}` };
    const account = mustSucceed(post('/api/v1/accounts', { name: 'Load', type: 'PERSONAL' }, auth), 'account');
    const wallet = mustSucceed(post(`/api/v1/accounts/${account.id}/wallets`, { currency: 'INR' }, auth), 'wallet');
    mustSucceed(post(`/api/v1/wallets/${wallet.id}/deposits`, { amount: STARTING_BALANCE },
        { ...auth, 'Idempotency-Key': `seed-${wallet.id}` }), 'deposit');
    users.push({ auth, walletId: wallet.id });
  }
  return { users };
}

export default function ({ users }) {
  const from = users[(__VU - 1) % users.length];
  let to = users[Math.floor(Math.random() * users.length)];
  if (to.walletId === from.walletId) to = users[(users.indexOf(from) + 1) % users.length];

  const res = http.post(`${BASE_URL}/api/v1/transfers`,
      JSON.stringify({ fromWalletId: from.walletId, toWalletId: to.walletId, amount: 1 + Math.floor(Math.random() * 500) }),
      {
        headers: { ...JSON_HEADERS, ...from.auth, 'Idempotency-Key': `${__VU}-${__ITER}-${Date.now()}` },
        tags: { name: 'transfer' },
      });

  if (res.status === 429) {
    rateLimited.add(1);
    sleep(1);
    return;
  }
  // 422 INSUFFICIENT_FUNDS is a correct answer when a wallet has been drained, not a failure.
  check(res, { 'transfer accepted or correctly refused': (r) => (r.status >= 200 && r.status < 300) || r.status === 422 },
      { name: 'transfer' });
}

// Transfers only move money between these wallets, so their total must be exactly what was deposited.
export function teardown({ users }) {
  let total = 0;
  for (const { auth, walletId } of users) {
    const wallet = mustSucceed(request('GET', `/api/v1/wallets/${walletId}`, null, auth), 'balance');
    if (wallet.balance < 0) moneyLost.add(1);
    total += wallet.balance;
  }
  const expected = STARTING_BALANCE * users.length;
  console.log(`Total across ${users.length} wallets: ${total} (expected ${expected})`);
  if (total !== expected) moneyLost.add(1);
}
