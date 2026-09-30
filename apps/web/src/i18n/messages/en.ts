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
  "page.home.intro": "Precious metals, priced live.",
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

  "metal.GOLD": "Gold",
  "metal.SILVER": "Silver",
  "metal.PLATINUM": "Platinum",
  "metal.PALLADIUM": "Palladium",
  "metal.RHODIUM": "Rhodium",
  "metal.RUTHENIUM": "Ruthenium",

  "marketdata.title": "Reference prices",
  "marketdata.caption": "Reference price per gram of pure metal, with the latest change",
  "marketdata.col.metal": "Metal",
  "marketdata.col.price": "Price",
  "marketdata.col.change": "Latest change",
  "marketdata.perGram": "per gram",
  "marketdata.updated": "Updated {time}",
  "marketdata.loading": "Loading prices",
  "marketdata.empty.title": "No prices yet",
  "marketdata.empty.description":
    "Reference prices appear here as soon as the first one is received.",
  "marketdata.change.up": "Up {amount} ({percent})",
  "marketdata.change.down": "Down {amount} ({percent})",
  "marketdata.change.unchanged": "Unchanged",
  "marketdata.change.unknown": "Change not yet available",
  "marketdata.stale.fetch":
    "These prices may be out of date: the last successful update was at {time}.",
  "marketdata.stale.feed":
    "The price feed has not updated since {time}; these prices may be out of date.",
  "marketdata.error":
    "Prices could not be refreshed. Showing the last known values; trying again automatically.",
  "marketdata.retry": "Retry now",

  "cart.itemCount_one": "{count} item",
  "cart.itemCount_other": "{count} items",
  "totals.net": "Net",
  "totals.tax": "Tax",
  "totals.total": "Total",
} as const;

export type MessageKey = keyof typeof en;
