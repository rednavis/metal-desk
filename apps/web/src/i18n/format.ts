import type { Locale } from "./locales";

/**
 * Every price, number and date the app shows is formatted here, for the active language, and
 * nowhere else. A component that called `Intl` itself would keep the language it had when it was
 * written and ignore a switch; `grep` over `src` (see the ledger) keeps this the only file that
 * does.
 *
 * Prices are formatted in **the currency the server sent with them**, never in the currency the
 * customer prefers: the server already converted (BRD FR-1.8), so a price is whatever it says it
 * is, and a settlement-currency total stays in the settlement currency.
 */
export interface Formatter {
  /** A price from the API, `{amount, currency}`, in the active language. */
  money(price: { amount: string; currency: string }): string;
  number(value: number): string;
  /** A date and time from an ISO-8601 instant. */
  dateTime(iso: string): string;
}

export function createFormatter(locale: Locale): Formatter {
  return {
    money: (price) =>
      new Intl.NumberFormat(locale, { style: "currency", currency: price.currency }).format(
        Number(price.amount),
      ),
    number: (value) => new Intl.NumberFormat(locale).format(value),
    dateTime: (iso) =>
      new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(
        new Date(iso),
      ),
  };
}
