import { useState } from 'react'
import { createApi, type Api } from './api/client'
import { Dashboard } from './components/Dashboard'
import { LoginForm } from './components/LoginForm'

export const SESSION_EXPIRED = 'Your session has ended. Please log in again.'

interface Props {
  /** Injected in tests. By default the app builds a client that uses the browser's fetch. */
  createClient?: typeof createApi
}

/**
 * The access token lives only in memory, inside this closure. It is never written to localStorage or
 * sessionStorage, so a script injected into the page cannot read it from storage and it does not outlive the tab.
 * The cost is that reloading the page logs the user out.
 */
export function App({ createClient = createApi }: Props) {
  const [email, setEmail] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | undefined>()

  const [{ api, setToken }] = useState(() => {
    let token: string | null = null
    const client: Api = createClient({
      getToken: () => token,
      onUnauthorized: () => {
        token = null
        setEmail(null)
        setNotice(SESSION_EXPIRED)
      },
    })
    return { api: client, setToken: (next: string | null) => (token = next) }
  })

  function handleLogin(token: string, userEmail: string) {
    setToken(token)
    setNotice(undefined)
    setEmail(userEmail)
  }

  function handleLogout() {
    setToken(null)
    setEmail(null)
    setNotice('You have logged out.')
  }

  if (!email) {
    return <LoginForm api={api} onLogin={handleLogin} notice={notice} />
  }
  return <Dashboard api={api} email={email} onLogout={handleLogout} />
}
