import type { MessageKey } from "./en";

/** The German catalogue. `satisfies` makes a missing or misspelt key a compile error. */
export const de = {
  "app.title": "MetalDesk",

  "nav.home": "Startseite",
  "nav.catalog": "Katalog",
  "nav.cart": "Warenkorb",
  "nav.signIn": "Anmelden",

  "page.home.title": "MetalDesk",
  "page.home.intro": "Edelmetalle zu Live-Preisen.",
  "page.catalog.title": "Katalog",
  "page.cart.title": "Warenkorb",
  "page.cart.empty.title": "Ihr Warenkorb ist leer",
  "page.cart.empty.description":
    "Legen Sie etwas aus dem Katalog hinein, um eine Bestellung zu beginnen.",
  "page.cart.empty.browse": "Katalog ansehen",
  "page.checkout.title": "Kasse",
  "page.signIn.title": "Anmelden",
  "page.notFound.title": "Seite nicht gefunden",
  "page.notFound.description": "Die gewünschte Seite existiert nicht.",
  "page.notFound.home": "Zur Startseite",

  "error.title": "Etwas ist schiefgelaufen",
  "error.reference": "Wenn Sie uns kontaktieren, nennen Sie die Referenz {reference} ({code}).",
  "error.unexpected":
    "Ein unerwarteter Fehler ist aufgetreten. Laden Sie die Seite neu und versuchen Sie es erneut.",
  "spinner.loading": "Wird geladen",

  "switcher.theme": "Darstellung",
  "switcher.language": "Sprache",
  "switcher.currency": "Währung",
  "theme.system": "Wie das Gerät",
  "theme.light": "Hell",
  "theme.dark": "Dunkel",
  "currency.fakeRates":
    "Preise in {currency} werden mit Demo-Wechselkursen umgerechnet, nicht mit echten Marktkursen. Kasse und Bestellungen sind immer in {settlement}.",
  "currency.settlementOnly": "Kasse und Bestellungen sind immer in {settlement}.",

  "metal.GOLD": "Gold",
  "metal.SILVER": "Silber",
  "metal.PLATINUM": "Platin",
  "metal.PALLADIUM": "Palladium",
  "metal.RHODIUM": "Rhodium",
  "metal.RUTHENIUM": "Ruthenium",

  "marketdata.title": "Referenzpreise",
  "marketdata.caption": "Referenzpreis pro Gramm Feinmetall, mit der letzten Änderung",
  "marketdata.col.metal": "Metall",
  "marketdata.col.price": "Preis",
  "marketdata.col.change": "Letzte Änderung",
  "marketdata.perGram": "pro Gramm",
  "marketdata.updated": "Aktualisiert {time}",
  "marketdata.loading": "Preise werden geladen",
  "marketdata.empty.title": "Noch keine Preise",
  "marketdata.empty.description":
    "Referenzpreise erscheinen hier, sobald der erste eingegangen ist.",
  "marketdata.change.up": "Gestiegen um {amount} ({percent})",
  "marketdata.change.down": "Gefallen um {amount} ({percent})",
  "marketdata.change.unchanged": "Unverändert",
  "marketdata.change.unknown": "Änderung noch nicht verfügbar",
  "marketdata.stale.fetch":
    "Diese Preise sind möglicherweise veraltet: die letzte erfolgreiche Aktualisierung war um {time}.",
  "marketdata.stale.feed":
    "Der Preisfeed wurde seit {time} nicht aktualisiert; diese Preise sind möglicherweise veraltet.",
  "marketdata.error":
    "Die Preise konnten nicht aktualisiert werden. Es werden die zuletzt bekannten Werte angezeigt; es wird automatisch erneut versucht.",
  "marketdata.retry": "Jetzt erneut versuchen",

  "cart.itemCount_one": "{count} Artikel",
  "cart.itemCount_other": "{count} Artikel",
  "totals.net": "Netto",
  "totals.tax": "Steuer",
  "totals.total": "Gesamt",
} as const satisfies Record<MessageKey, string>;
