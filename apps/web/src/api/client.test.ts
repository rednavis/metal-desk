import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./client";
import { ApiError } from "./errors";
import { createMemoryTokenStore } from "./tokenStore";
import { priceViewSchema } from "./types";

function reply(status: number, body?: unknown) {
  return vi.fn<typeof globalThis.fetch>(() =>
    Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status })),
  );
}

function sentHeaders(transport: ReturnType<typeof reply>): Headers {
  const init = transport.mock.calls[0]?.[1];
  return new Headers(init?.headers);
}

describe("the API client", () => {
  it("turns the server's error envelope into a typed error with the correlation id", async () => {
    const transport = reply(409, {
      code: "checkout.handoff-required",
      message: "This order needs a manager's price and terms",
      correlationId: "abc-123",
    });
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport });

    const failure = await client.get("/cart").catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({
      status: 409,
      code: "checkout.handoff-required",
      message: "This order needs a manager's price and terms",
      correlationId: "abc-123",
    });
  });

  it("carries a rejected form's field violations", async () => {
    const violations = [{ field: "email", code: "email.malformed", message: "Not an email" }];
    const client = createApiClient({
      baseUrl: "/api",
      fetchImpl: reply(400, {
        code: "request.invalid",
        message: "Invalid",
        correlationId: "c",
        violations,
      }),
    });

    const failure = (await client
      .put("/x", { body: {} })
      .catch((error: unknown) => error)) as ApiError;

    expect(failure.violations).toEqual(violations);
  });

  it("gives a failure that is not the envelope a code of its own and the id it sent", async () => {
    const transport = vi.fn<typeof globalThis.fetch>(() =>
      Promise.resolve(new Response("<html>Bad gateway</html>", { status: 502 })),
    );
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport });

    const failure = (await client.get("/x").catch((error: unknown) => error)) as ApiError;

    expect(failure.code).toBe("http.502");
    expect(failure.correlationId).toBe(sentHeaders(transport).get("X-Correlation-Id"));
  });

  it("reports a dropped connection as unreachable", async () => {
    const transport = vi.fn<typeof globalThis.fetch>(() => Promise.reject(new TypeError("down")));
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport });

    await expect(client.get("/x")).rejects.toMatchObject({
      code: "network.unreachable",
      status: 0,
    });
  });

  it("rejects a success body that does not match its schema", async () => {
    const client = createApiClient({ baseUrl: "/api", fetchImpl: reply(200, { amount: 5 }) });

    await expect(client.get("/price", { schema: priceViewSchema })).rejects.toMatchObject({
      code: "response.malformed",
    });
  });

  it("returns a body that matches its schema, with the query string and base url applied", async () => {
    const transport = reply(200, { amount: "1.00", currency: "EUR" });
    const client = createApiClient({ baseUrl: "/api/", fetchImpl: transport });

    const price = await client.get("/price", {
      schema: priceViewSchema,
      query: { page: 2, q: "gold bar", skip: undefined },
    });

    expect(price).toEqual({ amount: "1.00", currency: "EUR" });
    expect(transport.mock.calls[0]?.[0]).toBe("/api/price?page=2&q=gold+bar");
  });

  it("sends the bearer token when there is one, and none when there is not", async () => {
    const tokenStore = createMemoryTokenStore();
    const transport = reply(200, {});
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport, tokenStore });

    await client.get("/a");
    tokenStore.set("jwt-1");
    await client.get("/b");

    expect(new Headers(transport.mock.calls[0]?.[1]?.headers).get("authorization")).toBeNull();
    expect(new Headers(transport.mock.calls[1]?.[1]?.headers).get("authorization")).toBe(
      "Bearer jwt-1",
    );
  });

  it("clears the token and calls the unauthorized handler on a 401", async () => {
    const tokenStore = createMemoryTokenStore();
    tokenStore.set("jwt-1");
    const onUnauthorized = vi.fn();
    const client = createApiClient({
      baseUrl: "/api",
      fetchImpl: reply(401, { code: "auth.unauthorized", message: "Sign in", correlationId: "c" }),
      tokenStore,
      onUnauthorized,
    });

    await expect(client.get("/orders")).rejects.toMatchObject({ code: "auth.unauthorized" });

    expect(tokenStore.get()).toBeNull();
    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it("returns nothing for a 204", async () => {
    const client = createApiClient({ baseUrl: "/api", fetchImpl: reply(204) });

    await expect(client.delete("/cart")).resolves.toBeUndefined();
  });
});
