import { useCallback, useEffect, useState } from 'react'
import type { Api } from '../api/client'
import { messageFor } from '../api/problem'
import type { WalletEntry } from '../api/types'
import { LedgerTable } from './LedgerTable'
import { TransferForm } from './TransferForm'
import { WalletList, type WalletRow } from './WalletList'

interface Props {
  api: Api
  email: string
  onLogout: () => void
}

async function loadWallets(api: Api): Promise<WalletRow[]> {
  const accounts = await api.listAccounts()
  const perAccount = await Promise.all(
    accounts.map(async (account) =>
      (await api.listWallets(account.id)).map((wallet) => ({ ...wallet, accountName: account.name })),
    ),
  )
  return perAccount.flat()
}

export function Dashboard({ api, email, onLogout }: Props) {
  const [wallets, setWallets] = useState<WalletRow[] | null>(null)
  const [walletsError, setWalletsError] = useState<string | null>(null)
  const [selectedWalletId, setSelectedWalletId] = useState<string | null>(null)
  // Remembers which wallet the entries belong to, so a slow response never shows under the wrong wallet.
  const [entries, setEntries] = useState<{ walletId: string; items: WalletEntry[] } | null>(null)
  const [entriesError, setEntriesError] = useState<string | null>(null)
  // Bumped after a transfer, so both lists load again.
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let cancelled = false
    loadWallets(api)
      .then((rows) => {
        if (cancelled) return
        setWallets(rows)
        setWalletsError(null)
        setSelectedWalletId((current) => (current && rows.some((w) => w.id === current) ? current : rows[0]?.id ?? null))
      })
      .catch((error) => {
        if (!cancelled) setWalletsError(messageFor(error))
      })
    return () => {
      cancelled = true
    }
  }, [api, version])

  useEffect(() => {
    if (!selectedWalletId) return
    let cancelled = false
    api
      .walletEntries(selectedWalletId)
      .then((page) => {
        if (cancelled) return
        setEntries({ walletId: selectedWalletId, items: page.content })
        setEntriesError(null)
      })
      .catch((error) => {
        if (!cancelled) setEntriesError(messageFor(error))
      })
    return () => {
      cancelled = true
    }
  }, [api, selectedWalletId, version])

  const refresh = useCallback(() => setVersion((v) => v + 1), [])

  const selected = wallets?.find((w) => w.id === selectedWalletId)
  const selectedEntries = entries && entries.walletId === selectedWalletId ? entries.items : null

  return (
    <>
      <header className="topbar">
        <h1>PayCore</h1>
        <p>
          Signed in as <strong>{email}</strong>
        </p>
        <button type="button" className="secondary" onClick={onLogout}>
          Log out
        </button>
      </header>

      <main>
        <section className="card" aria-labelledby="wallets-title">
          <h2 id="wallets-title">Your wallets</h2>
          {walletsError && (
            <p className="error-text" role="alert">
              Could not load your wallets. {walletsError}
            </p>
          )}
          {!wallets && !walletsError && <p>Loading wallets…</p>}
          {wallets && <WalletList wallets={wallets} selectedWalletId={selectedWalletId} onSelect={setSelectedWalletId} />}
        </section>

        {wallets && wallets.length > 0 && (
          <>
            <section className="card" aria-labelledby="ledger-title">
              <h2 id="ledger-title">Recent ledger entries</h2>
              {entriesError && (
                <p className="error-text" role="alert">
                  Could not load ledger entries. {entriesError}
                </p>
              )}
              {!selectedEntries && !entriesError && <p>Loading entries…</p>}
              {selectedEntries && selected && (
                <LedgerTable
                  entries={selectedEntries}
                  caption={`Latest entries for the ${selected.currency} wallet in ${selected.accountName}`}
                />
              )}
            </section>

            <section className="card" aria-labelledby="send-money-title">
              <h2 id="send-money-title">Send money</h2>
              <TransferForm api={api} wallets={wallets} onTransferred={refresh} />
            </section>
          </>
        )}
      </main>
    </>
  )
}
