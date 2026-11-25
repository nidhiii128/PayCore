import { API_URL } from './support/api.ts'

/** Fails fast with a clear message when the backend is not running, instead of every test timing out. */
export default async function globalSetup() {
  try {
    const response = await fetch(`${API_URL}/actuator/health`)
    if (response.ok) return
    throw new Error(`status ${response.status}`)
  } catch (error) {
    throw new Error(
      `The PayCore backend is not reachable at ${API_URL} (${String(error)}). ` +
        'Start it first, for example with "docker compose up --build" in the repository root, ' +
        'or set PAYCORE_API_URL.',
      { cause: error },
    )
  }
}
