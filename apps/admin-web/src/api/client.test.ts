import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./client";
import { ApiError } from "./errors";
import { createMemoryTokenStore } from "./tokenStore";
import { tierViewSchema } from "./types";

function reply(status: number, body?: unknown) {
  return vi.fn<typeof globalThis.fetch>(() =>
    Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status })),
  );
}

describe("the admin API client", () => {
  it("turns the server's error envelope into a typed error with the correlation id", async () => {
    const client = createApiClient({
      baseUrl: "/api",
      fetchImpl: reply(409, {
        code: "order.changed",
        message: "Order o-1 changed while it was being updated",
        correlationId: "abc-123",
      }),
    });

    const failure = await client
      .post("/admin/orders/o-1/fulfillment")
      .catch((error: unknown) => error);

    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({ status: 409, code: "order.changed", correlationId: "abc-123" });
  });

  it("sends the bearer token when there is one, and nothing when there is not", async () => {
    const transport = reply(200, {});
    const tokenStore = createMemoryTokenStore();
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport, tokenStore });

    await client.get("/admin/tiers");
    tokenStore.set("abc");
    await client.get("/admin/tiers");

    const headers = (call: number) => new Headers(transport.mock.calls[call]?.[1]?.headers);
    expect(headers(0).has("Authorization")).toBe(false);
    expect(headers(1).get("Authorization")).toBe("Bearer abc");
  });

  it("drops the token on a 401, which is what sends the user back to the login form", async () => {
    const tokenStore = createMemoryTokenStore();
    tokenStore.set("expired");
    const client = createApiClient({
      baseUrl: "/api",
      tokenStore,
      fetchImpl: reply(401, {
        code: "auth.unauthorized",
        message: "A staff identity is required",
        correlationId: "c",
      }),
    });

    await expect(client.get("/admin/tiers")).rejects.toMatchObject({
      status: 401,
      code: "auth.unauthorized",
    });
    expect(tokenStore.get()).toBeNull();
  });

  it("keeps the token on any other failure", async () => {
    const tokenStore = createMemoryTokenStore();
    tokenStore.set("still-good");
    const client = createApiClient({ baseUrl: "/api", tokenStore, fetchImpl: reply(409, {}) });

    await client.get("/admin/tiers").catch(() => undefined);

    expect(tokenStore.get()).toBe("still-good");
  });

  it("reports a dropped connection as unreachable", async () => {
    const transport = vi.fn<typeof globalThis.fetch>(() => Promise.reject(new TypeError("down")));
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport });

    await expect(client.get("/admin/tiers")).rejects.toMatchObject({ code: "network.unreachable" });
  });

  it("rejects a success body that does not match its schema", async () => {
    const client = createApiClient({ baseUrl: "/api", fetchImpl: reply(200, { id: "t-1" }) });

    await expect(client.get("/admin/tiers/t-1", { schema: tierViewSchema })).rejects.toMatchObject({
      code: "response.malformed",
    });
  });
});
