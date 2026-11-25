// The API stores money as integers in minor units (1 INR = 100 paise). The user types major units ("12.50").
// Converting with strings and BigInt avoids floating point errors such as 0.1 + 0.2.

const LOCALE = 'en-US'

/** Same limit as MoneyRequest.MAX_AMOUNT in the backend. */
export const MAX_MINOR_UNITS = 1_000_000_000_000_000n

/** Number of decimal places for a currency: 2 for INR and USD, 0 for JPY. */
export function fractionDigits(currency: string): number {
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2
}

export function formatMoney(minorUnits: number, currency: string): string {
  const digits = fractionDigits(currency)
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency }).format(minorUnits / 10 ** digits)
}

export type ParsedAmount = { ok: true; minorUnits: number } | { ok: false; message: string }

export function parseAmount(input: string, currency: string): ParsedAmount {
  const text = input.trim()
  if (text === '') {
    return { ok: false, message: 'Enter an amount.' }
  }
  const digits = fractionDigits(currency)
  const match = /^(\d+)(?:\.(\d+))?$/.exec(text)
  if (!match) {
    return { ok: false, message: 'Enter the amount as a number, for example 12.50.' }
  }
  const [, whole, fraction = ''] = match
  if (fraction.length > digits) {
    return {
      ok: false,
      message: digits === 0
        ? `${currency} amounts cannot have decimals.`
        : `${currency} amounts can have at most ${digits} decimal places.`,
    }
  }
  const minor = BigInt(whole) * 10n ** BigInt(digits) + BigInt(fraction.padEnd(digits, '0') || '0')
  if (minor <= 0n) {
    return { ok: false, message: 'The amount must be more than zero.' }
  }
  if (minor > MAX_MINOR_UNITS) {
    return { ok: false, message: 'That amount is too large.' }
  }
  return { ok: true, minorUnits: Number(minor) }
}
