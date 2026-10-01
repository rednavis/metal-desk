import type { OrderStatus, TierView, TransitionTrigger } from "../api/types";

/**
 * A stand-in for `apps/admin`'s API. It keeps tiers and orders in memory and answers the way the
 * server does: a tier refused with the domain's own code, warnings on changes that matter, and for
 * orders the triggers its state machine accepts in `actions`. Those come from a table that lives here
 * and nowhere in the app, so a test of "the app follows the server" is honest. Every request is
 * recorded with its headers.
 */
export interface Sent {
  method: string;
  url: string;
  body: unknown;
  headers: Headers;
}

export interface FakeOrder {
  id: string;
  number: string;
  status: OrderStatus;
  /** Overrides what the state machine table would say. */
  actions?: TransitionTrigger[];
  net?: string;
  tax?: string;
  weightGrams?: string | null;
  boundCeiling?: "VALUE" | "WEIGHT" | "NO_TIER_FOR_REGION" | "WITHIN_TIERS";
  shipment?: { carrier: string; trackingReference: string };
}

const ACCEPTED: Partial<Record<OrderStatus, TransitionTrigger[]>> = {
  AWAITING_MANAGER_QUOTE: ["QUOTE_SET", "QUOTE_DECLINED"],
  PAID: ["FULFILLMENT_STARTED"],
  FULFILLING: ["SHIPPED"],
  SHIPPED: ["DELIVERED"],
};

export function order(overrides: Partial<FakeOrder> & Pick<FakeOrder, "id" | "status">): FakeOrder {
  return { number: `1000000${overrides.id}`, ...overrides };
}

export function tier(overrides: Partial<TierView> & Pick<TierView, "id" | "region">): TierView {
  return {
    valueCeiling: "5000.00",
    currency: "EUR",
    weightGrams: "2500",
    deliveryPrice: "12.50",
    minDays: 2,
    maxDays: 4,
    ...overrides,
  };
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const refusal = (status: number, code: string, message: string) =>
  json({ code, message, correlationId: "corr-1" }, status);

/** The users the server knows, the same two the first migration creates. */
export const USERS = [
  { login: "admin", password: "admin", email: "admin@admin.by", role: "ADMIN" },
  { login: "manager", password: "manager", email: "manager@manager.by", role: "MANAGER" },
] as const;

export function createAdminServer(
  initial: { tiers?: TierView[]; orders?: FakeOrder[]; throttled?: boolean } = {},
) {
  const throttled = initial.throttled ?? false;
  let expired = false;
  let tiers = [...(initial.tiers ?? [])];
  const orders = new Map((initial.orders ?? []).map((o) => [o.id, { ...o }]));
  const sent: Sent[] = [];

  const summary = (o: FakeOrder) => {
    const net = Number(o.net ?? "200.00");
    const tax = Number(o.tax ?? "38.00");
    return {
      id: o.id,
      number: o.number,
      status: o.status,
      customerId: "cust-1",
      itemCount: 2,
      total: (net + tax).toFixed(2),
      currency: "EUR",
      createdAt: "2026-09-30T10:00:00Z",
      updatedAt: "2026-09-30T11:00:00Z",
    };
  };

  const detail = (o: FakeOrder) => ({
    summary: summary(o),
    customerName: "Ann Example",
    contact: { email: "ann@example.com", phone: "+4930123456" },
    destination: "1 Main Street, 10115 Berlin, DE",
    lines: [
      {
        productId: "gold-bar",
        name: "Gold bar 1 oz",
        quantity: 2,
        unitPrice: "100.00",
        lineNet: o.net ?? "200.00",
        lineTax: o.tax ?? "38.00",
      },
    ],
    net: o.net ?? "200.00",
    tax: o.tax ?? "38.00",
    delivery: "0.00",
    ...(o.shipment ? { shipment: o.shipment } : {}),
    ...(o.status === "AWAITING_MANAGER_QUOTE"
      ? {
          handoff: {
            region: "DE",
            ...(o.weightGrams === null ? {} : { weightGrams: o.weightGrams ?? "3500" }),
            ...(o.weightGrams === null ? {} : { boundCeiling: o.boundCeiling ?? "WEIGHT" }),
          },
        }
      : {}),
    actions: o.actions ?? ACCEPTED[o.status] ?? [],
  });

  const page = (items: FakeOrder[], params: URLSearchParams) => {
    const p = Number(params.get("page") ?? 0);
    const size = Number(params.get("size") ?? 20);
    return json({
      items: items.slice(p * size, (p + 1) * size).map(summary),
      page: p,
      size,
      total: items.length,
    });
  };

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
    const path = url.pathname.replace(/^\/api\/admin/, "");
    const reply = (response: Response) => Promise.resolve(response);

    if (path === "/auth/sign-in" && method === "POST") {
      const attempt = body as { login?: string; password?: string };
      if (throttled) return reply(refusal(429, "auth.throttled", "Too many attempts"));
      const user = USERS.find((u) => u.login === attempt.login && u.password === attempt.password);
      if (!user) return reply(refusal(401, "auth.invalid-credentials", "Invalid credentials"));
      return reply(
        json({
          accessToken: `token-for-${user.login}`,
          tokenType: "Bearer",
          expiresInSeconds: 1800,
          login: user.login,
          role: user.role,
        }),
      );
    }
    if (path === "/auth/sign-out") return reply(new Response(null, { status: 204 }));

    // Everything else needs the token sign-in handed out, the way the server's filter chain does.
    const bearer = new Headers(init?.headers).get("Authorization");
    const caller = USERS.find((u) => bearer === `Bearer token-for-${u.login}`);
    if (!caller || expired) {
      return reply(refusal(401, "auth.unauthorized", "A staff identity is required"));
    }

    if (path === "/me") {
      return reply(json({ login: caller.login, email: caller.email, role: caller.role }));
    }

    if (path === "/tiers" && method === "GET") {
      const region = url.searchParams.get("region");
      return reply(json(tiers.filter((t) => region === null || t.region === region)));
    }
    if (path === "/tiers" && method === "POST") return reply(saveTier(undefined, body));
    const tierId = /^\/tiers\/([^/]+)$/.exec(path)?.[1];
    if (tierId !== undefined) {
      const existing = tiers.find((t) => t.id === tierId);
      if (!existing) return reply(refusal(404, "tier.not-found", `No tier ${tierId}`));
      if (method === "GET") return reply(json(existing));
      if (method === "PUT") return reply(saveTier(tierId, body));
      if (method === "DELETE") {
        tiers = tiers.filter((t) => t.id !== tierId);
        const last = !tiers.some((t) => t.region === existing.region);
        return reply(
          json({
            removedId: tierId,
            warnings: last
              ? [
                  {
                    code: "region.uncovered",
                    message: `Region ${existing.region} has no tier left: every order there will go to manager handoff`,
                  },
                ]
              : [],
          }),
        );
      }
    }

    if (path === "/quotes" && method === "GET") {
      return reply(
        page(
          [...orders.values()].filter((o) => o.status === "AWAITING_MANAGER_QUOTE"),
          url.searchParams,
        ),
      );
    }
    if (path === "/orders" && method === "GET") {
      const status = url.searchParams.get("status");
      return reply(
        page(
          [...orders.values()].filter((o) => status === null || o.status === status),
          url.searchParams,
        ),
      );
    }
    const id = /^\/(?:orders|quotes)\/([^/]+)(\/.*)?$/.exec(path);
    const found = id?.[1] ? orders.get(id[1]) : undefined;
    if (id && !found) return reply(refusal(404, "order.not-found", "No such order"));
    if (id && found) {
      const rest = id[2] ?? "";
      if (rest === "" && method === "GET") return reply(json(detail(found)));
      const accepts = (trigger: TransitionTrigger) =>
        (found.actions ?? ACCEPTED[found.status] ?? []).includes(trigger);
      const illegal = (trigger: TransitionTrigger) =>
        refusal(409, "order.illegal-transition", `${trigger} is not allowed from ${found.status}`);
      if (rest === "/fulfillment") {
        if (!accepts("FULFILLMENT_STARTED")) return reply(illegal("FULFILLMENT_STARTED"));
        found.status = "FULFILLING";
        found.actions = undefined;
        return reply(json(detail(found)));
      }
      if (rest === "/delivery") {
        if (!accepts("DELIVERED")) return reply(illegal("DELIVERED"));
        found.status = "DELIVERED";
        found.actions = undefined;
        return reply(json(detail(found)));
      }
      if (rest === "/shipment" && method === "PUT") {
        if (!accepts("SHIPPED")) return reply(illegal("SHIPPED"));
        found.status = "SHIPPED";
        found.actions = undefined;
        found.shipment = body as FakeOrder["shipment"];
        return reply(json(detail(found)));
      }
      if (rest === "/terms") {
        if (!accepts("QUOTE_SET")) return reply(illegal("QUOTE_SET"));
        const request = body as { deliveryPrice: string };
        found.status = "AWAITING_PAYMENT";
        found.actions = undefined;
        const total = (
          Number(found.net ?? "200.00") +
          Number(found.tax ?? "38.00") +
          Number(request.deliveryPrice)
        ).toFixed(2);
        return reply(
          json({
            orderId: found.id,
            number: found.number,
            status: found.status,
            deliveryPrice: request.deliveryPrice,
            total,
            currency: "EUR",
          }),
        );
      }
      if (rest === "/decline") {
        if (!accepts("QUOTE_DECLINED")) return reply(illegal("QUOTE_DECLINED"));
        found.status = "CANCELLED";
        found.actions = undefined;
        return reply(json({ orderId: found.id, number: found.number, status: found.status }));
      }
    }
    return reply(refusal(404, "http.404", "no"));
  };

  function saveTier(id: string | undefined, body: unknown): Response {
    const r = body as {
      region: string;
      valueCeiling: string;
      weightCeiling: string;
      weightUnit: string;
      currency: string;
      deliveryPrice: string;
      minDays: number;
      maxDays: number;
    };
    if (!(Number(r.valueCeiling) > 0) || !(Number(r.weightCeiling) > 0)) {
      return refusal(
        400,
        "fulfillment-tier.ceiling-invalid",
        "Tier ceilings must be present and greater than zero",
      );
    }
    if (!(Number(r.deliveryPrice) >= 0)) {
      return refusal(
        400,
        "fulfillment-tier.price-invalid",
        "Tier delivery price must be present and not negative",
      );
    }
    if (r.minDays < 1)
      return refusal(400, "transit-time.min-not-positive", "Transit days must be at least 1");
    if (r.maxDays < r.minDays)
      return refusal(
        400,
        "transit-time.range-inverted",
        "The most days must not be below the fewest",
      );
    const grams =
      r.weightUnit === "KILOGRAM" ? String(Number(r.weightCeiling) * 1000) : r.weightCeiling;
    const saved: TierView = {
      id: id ?? `tier-${String(tiers.length + 1)}`,
      region: r.region.toUpperCase(),
      valueCeiling: r.valueCeiling,
      currency: r.currency,
      weightGrams: grams,
      deliveryPrice: r.deliveryPrice,
      minDays: r.minDays,
      maxDays: r.maxDays,
    };
    tiers = [...tiers.filter((t) => t.id !== saved.id), saved];
    return json({ tier: saved, warnings: [] }, id === undefined ? 201 : 200);
  }

  return {
    fetchImpl,
    sent,
    orders,
    tiers: () => tiers,
    /** From now on every token is refused, as when it has expired. */
    expireTokens: () => {
      expired = true;
    },
  };
}
