import { DEFAULT_LOCALE, detectLocale, type Locale } from "../i18n/locales";
import type { Theme } from "../theme/theme";

/** The three things a customer can switch (BRD FR-1.6 to FR-1.8). */
export interface Preferences {
  theme: Theme;
  locale: Locale;
  /** The currency prices are shown in; the server converts. */
  currency: string;
}

/** What is known about a preference before defaults are applied: each field may be unset. */
export type PartialPreferences = Partial<Preferences>;

/** The currency orders are charged in, used until the customer picks another. */
export const DEFAULT_CURRENCY = "EUR";

/** The defaults: the language the browser prefers if we speak it, the device's theme, the settlement currency. */
export function defaultPreferences(browserLanguages: readonly string[] = []): Preferences {
  return {
    theme: "system",
    locale: browserLanguages.length > 0 ? detectLocale(browserLanguages) : DEFAULT_LOCALE,
    currency: DEFAULT_CURRENCY,
  };
}
