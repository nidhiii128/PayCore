import type { Wallet } from '../api/types'
import { formatMoney } from '../money'

export interface WalletRow extends Wallet {
  accountName: string
}

interface Props {
  wallets: WalletRow[]
  selectedWalletId: string | null
  onSelect: (walletId: string) => void
}

export function WalletList({ wallets, selectedWalletId, onSelect }: Props) {
  if (wallets.length === 0) {
    return <p>You have no wallets yet. The README shows how to create one through the API.</p>
  }
  return (
    <table>
      <caption className="visually-hidden">Your wallets and their balances</caption>
      <thead>
        <tr>
          <th scope="col">Account</th>
          <th scope="col">Currency</th>
          <th scope="col" className="num">
            Balance
          </th>
          <th scope="col">Status</th>
          <th scope="col">Wallet ID</th>
          <th scope="col">
            <span className="visually-hidden">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {wallets.map((wallet) => (
          <tr key={wallet.id} data-testid={`wallet-${wallet.id}`}>
            <td>{wallet.accountName}</td>
            <td>{wallet.currency}</td>
            <td className="num" data-testid="balance">
              {formatMoney(wallet.balance, wallet.currency)}
            </td>
            <td>{wallet.status === 'FROZEN' ? 'Frozen' : 'Active'}</td>
            <td>
              <code>{wallet.id}</code>
            </td>
            <td>
              <button
                type="button"
                className="secondary"
                aria-pressed={wallet.id === selectedWalletId}
                onClick={() => onSelect(wallet.id)}
              >
                Show entries<span className="visually-hidden"> for {wallet.currency} wallet in {wallet.accountName}</span>
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
