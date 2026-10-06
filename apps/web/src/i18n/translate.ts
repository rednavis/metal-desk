import { DEFAULT_LOCALE, LOCALES, type Locale } from "./locales";
import type { MessageKey } from "./messages/en";

export type Params = Record<string, string | number>;

/** Looks up a message, fills its `{placeholders}`, and picks a plural form if `count` is given. */
export type Translate = (key: MessageKey | PluralBase, params?: Params) => string;

/** The key of a plural pair without its `_one` / `_other` suffix. */
export type PluralBase = MessageKey extends infer K
  ? K extends `${infer Base}_one`
    ? Base
    : never
  : never;

export function createTranslator(locale: Locale): Translate {
  const messages = LOCALES[locale].messages;
  const fallback = LOCALES[DEFAULT_LOCALE].messages;
  const plurals = new Intl.PluralRules(locale);

  return (key, params = {}) => {
    const count = params["count"];
    const resolved =
      typeof count === "number"
        ? `${key}_${plurals.select(count) === "one" ? "one" : "other"}`
        : key;
    const template =
      (messages as Record<string, string>)[resolved] ??
      (fallback as Record<string, string>)[resolved] ??
      key;
    return template.replace(/\{(\w+)\}/g, (placeholder, name: string) =>
      name in params ? String(params[name]) : placeholder,
    );
  };
}
