import type { MessageKey } from "./en";

/** The German catalogue. `satisfies` makes a missing or misspelt key a compile error. */
export const de = {
  "app.title": "MetalDesk",

  "nav.home": "Startseite",
  "nav.catalog": "Katalog",
  "nav.cart": "Warenkorb",
  "nav.signIn": "Anmelden",

  "page.home.title": "MetalDesk",
  "page.home.intro": "Hier erscheinen bald aktuelle Referenzpreise und der Katalog.",
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

  "cart.itemCount_one": "{count} Artikel",
  "cart.itemCount_other": "{count} Artikel",
  "totals.net": "Netto",
  "totals.tax": "Steuer",
  "totals.total": "Gesamt",
} as const satisfies Record<MessageKey, string>;
