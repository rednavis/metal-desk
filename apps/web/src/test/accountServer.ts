import { json } from "./render";

/**
 * A stand-in for the account, order and inquiry endpoints. Its sign-in failures are what the real
 * server sends: unknown identifier and wrong password are the same 401, differing only in the random
 * correlation id. It knows two accounts in one account group (so a switch between them is allowed),
 * keeps an order list per account, and records every request. Anything it does not know is passed to
 * `fallback`, so a test can put a cart or a catalog behind it.
 */
export interface Account {
  id: string;
  email: string;
  password: string;
  orders: Order[];
}

export interface Order {
  number: string;
  status: string;
  statusLabel?: string;
  total: string;
  /** What the lines really add up to: the screen must not show this instead of `total`. */
  lineSum?: string;
  shipment?: { carrier: string; trackingReference: string };
}

export const ADA: Account = {
  id: "c-ada",
  email: "ada@example.test",
  password: "ada-secret-1",
  orders: [{ number: "100000000001", status: "PAID", statusLabel: "Paid", total: "119.00" }],
};

export const BOB: Account = {
  id: "c-bob",
  email: "bob@example.test",
  password: "bob-secret-1",
  orders: [
    {
      number: "100000000777",
      status: "SHIPPED",
      statusLabel: "Shipped",
      total: "999.00",
      lineSum: "250.00",
      shipment: { carrier: "DHL", trackingReference: "TRACK-42" },
    },
  ],
};

export interface AccountOptions {
  accounts?: Account[];
  /** After this many failed sign-ins the server answers 429. */
  throttleAfter?: number;
  /** Delays `/auth/me`, to look at the screen while the token is still being resolved. */
  holdMe?: Promise<void>;
  /** `/auth/me` answers 401, as for an expired token. */
  expired?: boolean;
  fallback?: typeof globalThis.fetch;
}

const money = (amount: string) => ({ amount, currency: "EUR" });

export function createAccountServer(options: AccountOptions = {}) {
  const accounts = options.accounts ?? [ADA, BOB];
  const sent: { method: string; url: string; body: unknown; headers: Headers }[] = [];
  let failures = 0;

  const byToken = (headers: Headers) => {
    const token = /^Bearer tok-(.+)$/.exec(headers.get("authorization") ?? "")?.[1];
    return accounts.find((account) => account.id === token);
  };
  const error = (status: number, code: string, message: string, extra: object = {}) =>
    Promise.resolve(json({ code, message, correlationId: crypto.randomUUID(), ...extra }, status));

  const fetchImpl: typeof globalThis.fetch = async (input, init) => {
    const target =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(target, "http://localhost");
    const method = init?.method ?? "GET";
    const headers = new Headers(init?.headers);
    const body: unknown = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    sent.push({ method, url: url.pathname + url.search, body, headers });
    const path = url.pathname.replace(/^\/api/, "");
    const reply = (value: unknown, status = 200) => Promise.resolve(json(value, status));
    const who = byToken(headers);

    if (path === "/auth/sign-in") {
      const { identifier, password } = body as { identifier: string; password: string };
      if (options.throttleAfter !== undefined && failures >= options.throttleAfter) {
        return error(429, "auth.throttled", "Too many attempts, try again later");
      }
      const account = accounts.find((a) => a.email === identifier);
      if (account?.password === password) {
        failures = 0;
        return reply({
          accessToken: `tok-${account.id}`,
          tokenType: "Bearer",
          expiresInSeconds: 900,
        });
      }
      failures += 1;
      return error(401, "auth.invalid-credentials", "Invalid credentials");
    }
    if (path === "/auth/sign-out") return Promise.resolve(new Response(null, { status: 204 }));
    if (path === "/auth/me") {
      await options.holdMe;
      if (options.expired || !who) return error(401, "auth.unauthorized", "Sign in");
      return reply({ customerId: who.id, verification: "VERIFIED" });
    }
    if (path === "/account/switch") {
      if (!who) return error(401, "auth.unauthorized", "Sign in");
      const target = accounts.find(
        (a) => a.id === (body as { targetCustomerId: string }).targetCustomerId,
      );
      if (!target) return error(403, "account.switch-denied", "That account is not available");
      return reply({ accessToken: `tok-${target.id}`, tokenType: "Bearer", expiresInSeconds: 900 });
    }
    if (path === "/account/register") {
      return reply({ reference: "reg-1", message: "Check your email" }, 202);
    }
    if (path === "/account/verify-email") {
      const { reference, code } = body as { reference: string; code: string };
      return reference === "reg-1" && code === "123456"
        ? reply({ verified: true })
        : error(400, "verification.invalid", "That code is not valid");
    }
    if (path === "/account/password-reset/request") {
      return reply(
        { message: "If that address belongs to an account, we have sent it a reset link" },
        202,
      );
    }
    if (path === "/account/password-reset/confirm") {
      const { reference, code, newPassword } = body as {
        reference: string;
        code: string;
        newPassword: string;
      };
      if (newPassword.length < 8) {
        return error(400, "password.invalid", "The password must be at least 8 characters");
      }
      return reference === "rst-1" && code === "token-ok"
        ? reply({ changed: true })
        : error(400, "verification.invalid", "That code is not valid");
    }
    if (path === "/orders" && method === "GET") {
      if (!who) return error(401, "auth.unauthorized", "Sign in");
      return reply({
        orders: who.orders.map((o) => ({
          orderNumber: o.number,
          createdAt: "2026-09-30T12:00:00Z",
          itemCount: 2,
          total: money(o.total),
          status: o.status,
          statusLabel: o.statusLabel ?? o.status,
        })),
      });
    }
    const detail = /^\/orders\/([^/]+)$/.exec(path);
    if (detail) {
      const order = who?.orders.find((o) => o.number === detail[1]);
      if (!order) return error(404, "order.not-found", "No such order");
      return reply({
        orderNumber: order.number,
        createdAt: "2026-09-30T12:00:00Z",
        itemCount: 2,
        status: order.status,
        statusLabel: order.statusLabel ?? order.status,
        lines: [
          {
            productName: "Gold Bar 100 g",
            quantity: 2,
            unitPrice: money("100.00"),
            lineNet: money(order.lineSum ?? "200.00"),
            taxRatePercent: "19.00",
            lineTax: money("38.00"),
          },
        ],
        deliveryAddress: {
          street: "Main St 1",
          postalCode: "10115",
          city: "Berlin",
          country: "DE",
        },
        totals: {
          net: money("200.00"),
          tax: money("38.00"),
          delivery: money("14.90"),
          grandTotal: money(order.total),
        },
        paymentMethod: "CARD",
        ...(order.shipment ? { shipment: order.shipment } : {}),
      });
    }
    if (path === "/inquiries" && method === "POST") {
      const request = body as Record<string, string>;
      const violations = (["name", "email", "topic", "message"] as const)
        .filter((field) => !request[field])
        .map((field) => ({ field, code: "required", message: `${field} is required` }));
      if (violations.length > 0) {
        return error(400, "validation.failed", "The form has errors", { violations });
      }
      return reply({ reference: "INQ-ABC123", message: "Thank you. We have your message." }, 201);
    }
    return options.fallback ? options.fallback(input, init) : error(404, "http.404", "no");
  };

  return { fetchImpl, sent };
}
