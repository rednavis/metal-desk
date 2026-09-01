import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./client";
import { createRequestContext, withLocale } from "./requestContext";

function setup(locale = "de", currency = "USD") {
  const transport = vi.fn<typeof globalThis.fetch>(() =>
    Promise.resolve(new Response("{}", { status: 200 })),
  );
  const client = createApiClient({
    baseUrl: "/api",
    fetchImpl: transport,
    requestContext: createRequestContext({ locale, currency }),
  });
  const last = () => {
    const call = transport.mock.calls.at(-1);
    return {
      url: typeof call?.[0] === "string" ? call[0] : "",
      headers: new Headers(call?.[1]?.headers),
      body:
        typeof call?.[1]?.body === "string"
          ? (JSON.parse(call[1].body) as Record<string, unknown>)
          : undefined,
    };
  };
  return { client, last };
}

describe("the customer's language and currency on requests", () => {
  it("names the language on every request", async () => {
    const { client, last } = setup();

    await client.get("/account/preferences");

    expect(last().headers.get("Accept-Language")).toBe("de");
  });

  it("asks the server to convert prices for the catalog and the cart, and only those", async () => {
    const { client, last } = setup();

    await client.get("/catalog/products/p1");
    expect(last().url).toBe("/api/catalog/products/p1?currency=USD");
    await client.get("/cart");
    expect(last().url).toBe("/api/cart?currency=USD");
    await client.post("/cart/lines", { body: { productId: "p1" } });
    expect(last().url).toBe("/api/cart/lines?currency=USD");
    await client.get("/currencies");
    expect(last().url).toBe("/api/currencies");
    await client.get("/orders");
    expect(last().url).toBe("/api/orders");
  });

  it("lets a call choose its own currency", async () => {
    const { client, last } = setup();

    await client.get("/cart", { query: { currency: "EUR" } });

    expect(last().url).toBe("/api/cart?currency=EUR");
  });

  it.each([
    ["POST", "/account/register"],
    ["POST", "/account/password-reset/request"],
    ["PUT", "/checkout/sessions/abc/step1"],
    ["POST", "/checkout/sessions/abc/handoff"],
    ["POST", "/checkout/sessions/abc/payment/execute"],
    ["POST", "/inquiries"],
  ])("sends the locale in the body of %s %s, which triggers mail", async (method, path) => {
    const { client, last } = setup();

    await (method === "PUT"
      ? client.put(path, { body: { name: "Ann" } })
      : client.post(path, { body: { name: "Ann" } }));

    expect(last().body).toMatchObject({ name: "Ann", locale: "de" });
  });

  it("sends a body with just the locale when a mailing request has none", async () => {
    const { client, last } = setup();

    await client.post("/checkout/sessions/abc/handoff");

    expect(last().body).toEqual({ locale: "de" });
  });

  it("does not add a locale to requests that send no mail, nor override one the call chose", async () => {
    const { client, last } = setup();

    await client.post("/cart/lines", { body: { productId: "p1" } });
    expect(last().body).toEqual({ productId: "p1" });
    await client.post("/account/register", { body: { name: "Ann", locale: "en" } });
    expect(last().body).toMatchObject({ locale: "en" });
  });

  it("never adds a locale to a read", () => {
    expect(withLocale("GET", "/account/register", undefined, "de")).toBeUndefined();
  });
});
