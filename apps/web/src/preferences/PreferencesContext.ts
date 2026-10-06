import { createContext } from "react";
import type { Formatter } from "../i18n/format";
import type { Locale } from "../i18n/locales";
import type { Translate } from "../i18n/translate";
import type { Theme } from "../theme/theme";
import type { Preferences } from "./model";

export interface PreferencesValue extends Preferences {
  setTheme: (theme: Theme) => void;
  setLocale: (locale: Locale) => void;
  setCurrency: (currency: string) => void;
  /** Translates a message into the active language. */
  t: Translate;
  /** Formats prices, numbers and dates for the active language. */
  format: Formatter;
}

export const PreferencesContext = createContext<PreferencesValue | null>(null);
