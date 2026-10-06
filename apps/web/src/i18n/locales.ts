import { de } from "./messages/de";
import { en, type MessageKey } from "./messages/en";

/** The languages the storefront is translated into. Adding one means a catalogue here and, for mail, in `libs/mail`. */
export const LOCALES = {
  en: { name: "English", messages: en as Record<MessageKey, string> },
  de: { name: "Deutsch", messages: de as Record<MessageKey, string> },
} as const;

export type Locale = keyof typeof LOCALES;

export const DEFAULT_LOCALE: Locale = "en";

export function isLocale(value: unknown): value is Locale {
  return typeof value === "string" && value in LOCALES;
}

/** The first of the browser's preferred languages the storefront speaks, else the default. */
export function detectLocale(preferred: readonly string[]): Locale {
  for (const tag of preferred) {
    const language = tag.toLowerCase().split("-")[0];
    if (isLocale(language)) return language;
  }
  return DEFAULT_LOCALE;
}
