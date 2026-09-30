import type { PreferencesView } from "../api/types";
import { json } from "./render";

/**
 * A stand-in for the API, just enough for the preference tests: the currency options (with fake
 * rates, as the real server reports them), a server-side preference per "account", and a cart
 * whose amounts the "server" converts when asked for a currency. It records every request, so a
 * test can assert what was sent.
 */
export interface Sent {
  method: string;
  url: string;
  headers: Headers;
  body: unknown;
}

const RATE = 2;

function price(euro: number, currency: string) {
  const amount = currency === "USD" ? euro * RATE : euro;
  return { amount: amount.toFixed(2), currency };
}

function cart(currency: string) {
  return {
    cartId: "c1",
    empty: false,
    itemCount: 2,
    complete: true,
    lines: [],
    totals: { net: price(100, currency), tax: price(19, currency), total: price(119, currency) },
  };
}

export function createFakeServer(initial: PreferencesView = {}) {
  let stored: PreferencesView = initial;
  const sent: Sent[] = [];

  const fetchImpl: typeof globalThis.fetch = (input, init) => {
    const target =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(target, "http://localhost");
    const method = init?.method ?? "GET";
    const body: unknown = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    sent.push({
      method,
      url: url.pathname + url.search,
      headers: new Headers(init?.headers),
      body,
    });

    if (url.pathname === "/api/currencies") {
      return Promise.resolve(
        json({
          settlement: "EUR",
          rateSource: "FAKE",
          options: [
            { code: "EUR", perSettlementUnit: "1" },
            { code: "USD", perSettlementUnit: "2.00" },
          ],
        }),
      );
    }
    if (url.pathname === "/api/account/preferences") {
      if (method === "PUT") stored = body as PreferencesView;
      return Promise.resolve(json(stored));
    }
    if (url.pathname === "/api/cart") {
      return Promise.resolve(json(cart(url.searchParams.get("currency") ?? "EUR")));
    }
    return Promise.resolve(json({ code: "http.404", message: "no", correlationId: "c" }, 404));
  };

  return { fetchImpl, sent, stored: () => stored };
}
