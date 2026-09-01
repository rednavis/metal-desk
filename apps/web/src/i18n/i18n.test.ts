import { describe, expect, it } from "vitest";
import { createFormatter } from "./format";
import { LOCALES, detectLocale } from "./locales";
import { createTranslator } from "./translate";

describe("the message catalogues", () => {
  it("offer every key in every language, each with the same placeholders", () => {
    const english = LOCALES.en.messages as Record<string, string>;
    for (const [code, language] of Object.entries(LOCALES)) {
      const messages = language.messages as Record<string, string>;
      expect(Object.keys(messages).sort(), code).toEqual(Object.keys(english).sort());
      for (const [key, message] of Object.entries(messages)) {
        const placeholders = (text: string) =>
          [...text.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort();
        expect(placeholders(message), `${code} ${key}`).toEqual(placeholders(english[key] ?? ""));
      }
    }
  });

  it("has at least two languages", () => {
    expect(Object.keys(LOCALES).length).toBeGreaterThanOrEqual(2);
  });
});

describe("translating", () => {
  it("fills placeholders", () => {
    expect(createTranslator("en")("error.reference", { reference: "r-1", code: "x" })).toContain(
      "r-1",
    );
  });

  it("picks the plural form for the language", () => {
    const en = createTranslator("en");
    expect(en("cart.itemCount", { count: 1 })).toBe("1 item");
    expect(en("cart.itemCount", { count: 3 })).toBe("3 items");
    expect(createTranslator("de")("cart.itemCount", { count: 3 })).toBe("3 Artikel");
  });

  it("detects the first supported browser language", () => {
    expect(detectLocale(["fr-FR", "de-AT", "en"])).toBe("de");
    expect(detectLocale(["fr"])).toBe("en");
  });
});

describe("formatting", () => {
  const price = { amount: "1234.50", currency: "EUR" };

  it("formats a price for the active language, in the currency it arrived in", () => {
    expect(createFormatter("en").money(price)).toBe("€1,234.50");
    expect(createFormatter("de").money(price)).toMatch(/^1\.234,50\s€$/);
    expect(createFormatter("en").money({ amount: "5.00", currency: "USD" })).toBe("$5.00");
  });

  it("formats numbers and dates for the active language", () => {
    expect(createFormatter("de").number(1234.5)).toBe("1.234,5");
    expect(createFormatter("en").dateTime("2026-10-01T10:00:00Z")).toContain("2026");
  });
});
