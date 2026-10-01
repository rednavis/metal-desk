import { json } from "./render";

/**
 * A stand-in for the checkout endpoints that keeps a session the way `services/api` does: step 1
 * stores details and **drops the delivery evaluation**, a handoff session refuses payment endpoints
 * with 409, a decline keeps everything, and a confirmation exists only once an order was placed. The
 * amounts it reports are chosen by the test, deliberately not the sums of each other, so a screen
 * that added them up itself would show something else. Every request is recorded.
 */
export type Exceeded = "VALUE" | "WEIGHT" | "NO_TIER";

export interface CheckoutOptions {
  /** Which ceiling (if any) puts the order with a manager. */
  exceeded?: Exceeded;
  /** The methods the server offers, as `[method, group]`. */
  offered?: [string, string][];
  highValue?: boolean;
  /** Step 1 violations to answer with (the form is refused while this is set). */
  violations?: { field: string; code: string; message: string }[];
  /** The results of successive payment attempts; the last repeats. */
  executes?: Record<string, unknown>[];
  /** The result of the return callback. */
  callback?: Record<string, unknown>;
  /** Start from a session that is already this far along, to test a reload. */
  initial?: { details?: boolean; delivery?: boolean; method?: string; placed?: "PAID" | "INVOICE" };
  profile?: Record<string, string>;
  /** The overview carries an order number, as it does once an order exists (payment started or approved). */
  orderExists?: boolean;
  /** An empty basket makes starting a checkout fail, as it does on the server. */
  emptyBasket?: boolean;
}

const money = (amount: string) => ({ amount, currency: "EUR" });

const LINE = {
  productId: "p1",
  name: "Gold Bar 100 g",
  quantity: 2,
  maxQuantity: 10,
  pricingMode: "FIXED",
  unitPrice: money("100.00"),
  lineNet: money("200.00"),
  taxRatePercent: "19.00",
  lineTax: money("38.00"),
};

const DETAILS = {
  name: "Ada Lovelace",
  email: "ada@example.test",
  phone: "+4930123456",
  street: "Main St 1",
  city: "Berlin",
  country: "DE",
  postalCode: "10115",
};

export const OFFER_ALL: [string, string][] = [
  ["CARD", "GATEWAY"],
  ["BANK_DEBIT", "GATEWAY"],
  ["BANK_REDIRECT", "GATEWAY"],
  ["BANK_TRANSFER", "GATEWAY"],
  ["SAVED_WALLET", "GATEWAY"],
  ["WALLET_ACCOUNT", "WALLET"],
  ["INVOICE", "INVOICE"],
];

export function createCheckoutServer(options: CheckoutOptions = {}) {
  const sent: { method: string; url: string; body: unknown; headers: Headers }[] = [];
  let details: Record<string, string> | undefined = options.initial?.details ? DETAILS : undefined;
  let conversion: { reference: string; verified: boolean } | undefined;
  let delivery: Record<string, unknown> | undefined;
  let handoffReference: string | undefined;
  let selected: string | undefined = options.initial?.method;
  let placed: "PAID" | "INVOICE" | undefined = options.initial?.placed;
  let executed = 0;

  function evaluation() {
    const base = { exTaxValue: money("200.00"), weightGrams: "200" };
    if (options.exceeded === undefined) {
      return {
        ...base,
        stage: "PAYMENT_ALLOWED",
        quote: {
          tierId: "eu-standard",
          cost: money("14.90"),
          minDays: 2,
          maxDays: 4,
          quotedAt: "2026-10-01T10:00:00Z",
        },
      };
    }
    const reason = {
      VALUE: "VALUE_CEILING_EXCEEDED",
      WEIGHT: "WEIGHT_CEILING_EXCEEDED",
      NO_TIER: "NO_TIER_FOR_REGION",
    }[options.exceeded];
    return {
      ...base,
      stage: "HANDOFF_REQUIRED",
      reason,
      ...(options.exceeded === "NO_TIER" ? {} : { boundCeiling: options.exceeded }),
    };
  }
  if (options.initial?.delivery) delivery = evaluation();

  function session() {
    return {
      checkoutId: "cs1",
      source: "CART",
      basket: {
        cartId: "cart-1",
        empty: false,
        itemCount: 2,
        complete: true,
        lines: [LINE],
        totals: { net: money("200.00"), tax: money("38.00"), total: money("238.00") },
      },
      ...(details
        ? { details, consent: { policyVersion: "2026-10", acceptedAt: "2026-10-01T10:00:00Z" } }
        : {}),
      ...(conversion ? { conversion } : {}),
      ...(delivery
        ? { delivery: { ...delivery, ...(handoffReference ? { handoffReference } : {}) } }
        : {}),
    };
  }

  const error = (status: number, code: string, message: string, extra: object = {}) =>
    Promise.resolve(json({ code, message, correlationId: "server-corr", ...extra }, status));

  const fetchImpl: typeof globalThis.fetch = (input, init) => {
    const target =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(target, "http://localhost");
    const method = init?.method ?? "GET";
    const body: unknown = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    sent.push({
      method,
      url: url.pathname + url.search,
      body,
      headers: new Headers(init?.headers),
    });
    const path = url.pathname.replace(/^\/api/, "");
    const reply = (value: unknown, status = 200) => Promise.resolve(json(value, status));

    if (path === "/cart/delivery-profile") {
      return options.profile
        ? reply(options.profile)
        : Promise.resolve(new Response(null, { status: 204 }));
    }
    if (path === "/checkout/sessions" && method === "POST") {
      if (options.emptyBasket) return error(400, "checkout.basket-empty", "Your cart is empty");
      return reply(session(), 201);
    }
    if (path === "/checkout/payment/callback") {
      return reply(options.callback ?? { result: "CAPTURED", orderReference: "100000000001" });
    }
    const match = /^\/checkout\/sessions\/([^/]+)(\/.*)?$/.exec(path);
    if (!match) return error(404, "http.404", "no");
    const rest = match[2] ?? "";

    if (rest === "" && method === "GET") return reply(session());
    if (rest === "/step1" && method === "PUT") {
      const form = body as Record<string, unknown>;
      const violations = [...(options.violations ?? [])];
      if (form["privacyPolicyAccepted"] !== true) {
        violations.push({
          field: "privacyPolicyAccepted",
          code: "consent.privacy-required",
          message: "Accept the privacy policy",
        });
      }
      if (violations.length > 0) {
        return error(400, "validation.failed", "The form has errors", { violations });
      }
      const contact = form["contact"] as { email: string; phone: string };
      details = {
        name: String(form["name"]),
        email: contact.email,
        phone: contact.phone,
        street: String(form["street"]),
        city: String(form["city"]),
        country: String(form["country"]),
        postalCode: String(form["postalCode"]),
      };
      delivery = undefined; // the address may have changed: the quote is no longer valid
      selected = undefined;
      const account = form["account"] as { rememberMe: boolean };
      if (account.rememberMe) conversion = { reference: "conv-1", verified: false };
      return reply({
        checkoutId: "cs1",
        step1Complete: true,
        details,
        consent: { policyVersion: "2026-10", acceptedAt: "2026-10-01T10:00:00Z" },
        ...(conversion ? { conversion } : {}),
      });
    }
    if (rest === "/delivery/evaluate" && method === "POST") {
      delivery = evaluation();
      return reply(delivery);
    }
    if (rest === "/handoff" && method === "POST") {
      if (options.exceeded === undefined) {
        return error(409, "checkout.handoff-not-required", "No manager is needed");
      }
      handoffReference = "100000000042";
      return reply({
        reference: handoffReference,
        reason: (delivery as { reason: string }).reason,
      });
    }
    if (options.exceeded !== undefined && rest.startsWith("/payment")) {
      return error(409, "checkout.handoff-required", "A manager must handle this order");
    }
    if (rest === "/payment/methods" && method === "GET") {
      return reply({
        methods: (options.offered ?? OFFER_ALL).map(([m, group]) => ({ method: m, group })),
        highValue: options.highValue ?? false,
        grandTotal: money("999.99"),
        ...(selected ? { selected } : {}),
      });
    }
    if (rest === "/payment/method" && method === "PUT") {
      selected = (body as { method: string }).method;
      return reply({
        methods: (options.offered ?? OFFER_ALL).map(([m, group]) => ({ method: m, group })),
        highValue: options.highValue ?? false,
        grandTotal: money("999.99"),
        selected,
      });
    }
    if (rest === "/overview" && method === "GET") {
      return reply({
        checkoutId: "cs1",
        details,
        ...(selected ? { paymentMethod: selected } : {}),
        ...(options.orderExists ? { orderReference: "100000000001" } : {}),
        lines: [LINE],
        delivery: (delivery as { quote: unknown }).quote,
        // Not the sum of its parts on purpose: the screen must show what the server says.
        totals: {
          net: money("200.00"),
          tax: money("38.00"),
          delivery: money("14.90"),
          grandTotal: money("999.99"),
        },
      });
    }
    if (rest === "/payment/execute" && method === "POST") {
      const results = options.executes ?? [{ result: "CAPTURED", orderReference: "100000000001" }];
      const result = results[Math.min(executed, results.length - 1)] ?? {};
      executed += 1;
      if (result["result"] === "CAPTURED") placed = "PAID";
      if (result["result"] === "DOCUMENT_ISSUED") placed = "INVOICE";
      return reply(result);
    }
    if (rest === "/confirmation" && method === "GET") {
      if (!placed) return error(409, "checkout.not-confirmable", "Nothing has been placed");
      return reply({
        kind: placed,
        orderNumber: "100000000001",
        status: placed === "PAID" ? "PAID" : "AWAITING_PAYMENT",
        statusLabel: placed === "PAID" ? "Paid" : "Awaiting payment",
        itemCount: 2,
        total: money("999.99"),
        ...(placed === "INVOICE" ? { invoiceNumber: "INV-7" } : {}),
        message:
          placed === "PAID" ? "We are preparing your order." : "Pay the invoice we emailed you.",
      });
    }
    return error(404, "http.404", "no");
  };

  return {
    fetchImpl,
    sent,
    count: (method: string, suffix: string) =>
      sent.filter((r) => r.method === method && r.url.endsWith(suffix)).length,
  };
}
