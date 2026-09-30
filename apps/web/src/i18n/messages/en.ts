/**
 * The English message catalogue, and the definition of what a catalogue contains: {@link MessageKey}
 * is derived from these keys, so another language must provide every one of them (the compiler
 * checks `de.ts` against this file). A key ending `_one` / `_other` is a plural pair, chosen by the
 * `count` parameter.
 */
export const en = {
  "app.title": "MetalDesk",

  "nav.home": "Home",
  "nav.catalog": "Catalog",
  "nav.cart": "Cart",
  "nav.signIn": "Sign in",

  "page.home.title": "MetalDesk",
  "page.home.intro": "Live reference prices and the catalog will appear here.",
  "page.catalog.title": "Catalog",
  "page.cart.title": "Cart",
  "page.cart.empty.title": "Your cart is empty",
  "page.cart.empty.description": "Add something from the catalog to start an order.",
  "page.cart.empty.browse": "Browse the catalog",
  "page.checkout.title": "Checkout",
  "page.signIn.title": "Sign in",
  "page.notFound.title": "Page not found",
  "page.notFound.description": "The page you asked for does not exist.",
  "page.notFound.home": "Go to the home page",

  "error.title": "Something went wrong",
  "error.reference": "If you contact us, quote reference {reference} ({code}).",
  "error.unexpected": "An unexpected error occurred. Reload the page and try again.",
  "spinner.loading": "Loading",

  "switcher.theme": "Theme",
  "switcher.language": "Language",
  "switcher.currency": "Currency",
  "theme.system": "Follow device",
  "theme.light": "Light",
  "theme.dark": "Dark",
  "currency.fakeRates":
    "Prices in {currency} are converted at demo exchange rates, not real market rates. Checkout and orders are always in {settlement}.",
  "currency.settlementOnly": "Checkout and orders are always in {settlement}.",

  "cart.itemCount_one": "{count} item",
  "cart.itemCount_other": "{count} items",
  "totals.net": "Net",
  "totals.tax": "Tax",
  "totals.total": "Total",
} as const;

export type MessageKey = keyof typeof en;
