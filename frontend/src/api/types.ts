// Shapes of the PayCore REST API responses used by this app. Amounts are integers in minor units (paise, cents).

export interface AuthResponse {
  userId: string
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Account {
  id: string
  name: string
  type: 'PERSONAL' | 'BUSINESS'
  createdAt: string
}

export interface Wallet {
  id: string
  accountId: string
  currency: string
  balance: number
  status: 'ACTIVE' | 'FROZEN'
  createdAt: string
  updatedAt: string
}

export interface WalletEntry {
  entryId: string
  transactionId: string
  type: 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER'
  direction: 'DEBIT' | 'CREDIT'
  amount: number
  currency: string
  balanceAfter: number
  description?: string
  createdAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TransferRequest {
  fromWalletId: string
  toWalletId: string
  amount: number
  description?: string
}

export interface Transaction {
  id: string
  type: string
  status: string
  amount: number
  currency: string
  description?: string
  fromWalletId: string
  toWalletId: string
  createdAt: string
}
