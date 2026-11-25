import { describe, expect, it } from 'vitest'
import { formatMoney, fractionDigits, parseAmount } from './money'

describe('parseAmount', () => {
  it('converts major units to integer minor units without floating point errors', () => {
    expect(parseAmount('12.50', 'INR')).toEqual({ ok: true, minorUnits: 1250 })
    expect(parseAmount('0.29', 'USD')).toEqual({ ok: true, minorUnits: 29 })
    expect(parseAmount(' 7 ', 'INR')).toEqual({ ok: true, minorUnits: 700 })
  })

  it('uses the number of decimals of the currency', () => {
    expect(fractionDigits('JPY')).toBe(0)
    expect(parseAmount('500', 'JPY')).toEqual({ ok: true, minorUnits: 500 })
    expect(parseAmount('5.5', 'JPY')).toEqual({ ok: false, message: 'JPY amounts cannot have decimals.' })
    expect(parseAmount('1.234', 'INR')).toEqual({ ok: false, message: 'INR amounts can have at most 2 decimal places.' })
  })

  it('rejects empty, zero, negative and non numeric input', () => {
    expect(parseAmount('', 'INR').ok).toBe(false)
    expect(parseAmount('0.00', 'INR')).toEqual({ ok: false, message: 'The amount must be more than zero.' })
    expect(parseAmount('-5', 'INR').ok).toBe(false)
    expect(parseAmount('1e3', 'INR').ok).toBe(false)
    expect(parseAmount('12,50', 'INR').ok).toBe(false)
  })

  it('rejects amounts above the backend limit', () => {
    expect(parseAmount('10000000000000', 'INR')).toEqual({ ok: true, minorUnits: 1_000_000_000_000_000 })
    expect(parseAmount('10000000000000.01', 'INR')).toEqual({ ok: false, message: 'That amount is too large.' })
  })
})

describe('formatMoney', () => {
  it('formats minor units in the currency', () => {
    expect(formatMoney(50000, 'INR')).toBe('₹500.00')
    expect(formatMoney(1999, 'USD')).toBe('$19.99')
    expect(formatMoney(500, 'JPY')).toBe('¥500')
  })
})
