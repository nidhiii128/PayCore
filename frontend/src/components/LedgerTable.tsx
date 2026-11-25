import type { WalletEntry } from '../api/types'
import { formatMoney } from '../money'

const TYPE_LABELS: Record<WalletEntry['type'], string> = {
  DEPOSIT: 'Deposit',
  WITHDRAWAL: 'Withdrawal',
  TRANSFER: 'Transfer',
}

interface Props {
  entries: WalletEntry[]
  caption: string
}

/** One wallet's statement, newest first. A CREDIT adds money to the wallet and a DEBIT takes it out. */
export function LedgerTable({ entries, caption }: Props) {
  if (entries.length === 0) {
    return <p>No ledger entries yet for this wallet.</p>
  }
  return (
    <table>
      <caption>{caption}</caption>
      <thead>
        <tr>
          <th scope="col">Date</th>
          <th scope="col">Type</th>
          <th scope="col">Description</th>
          <th scope="col" className="num">
            Amount
          </th>
          <th scope="col" className="num">
            Balance after
          </th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => {
          const moneyIn = entry.direction === 'CREDIT'
          return (
            <tr key={entry.entryId}>
              <td>
                <time dateTime={entry.createdAt}>{new Date(entry.createdAt).toLocaleString()}</time>
              </td>
              <td>
                {TYPE_LABELS[entry.type] ?? entry.type} {moneyIn ? 'in' : 'out'}
              </td>
              <td>{entry.description ?? ''}</td>
              <td className={moneyIn ? 'num money-in' : 'num money-out'}>
                {moneyIn ? '+' : '-'}
                {formatMoney(entry.amount, entry.currency)}
              </td>
              <td className="num">{formatMoney(entry.balanceAfter, entry.currency)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
