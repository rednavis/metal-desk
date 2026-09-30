import { describe, expect, it, vi } from "vitest";
import { createApiClient } from "./client";
import { ApiError } from "./errors";
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

  it("sends no credential: nothing in the request identifies a user, the proxy does that", async () => {
    const transport = reply(200, {});
    const client = createApiClient({ baseUrl: "/api", fetchImpl: transport });

    await client.get("/admin/tiers");

    const sent = Object.keys(transport.mock.calls[0]?.[1]?.headers ?? {}).map((name) =>
      name.toLowerCase(),
    );
    expect(sent.filter((name) => name.startsWith("auth") || name.includes("token"))).toEqual([]);
    expect(transport.mock.calls[0]?.[1]?.credentials).toBe("same-origin");
  });

  it("reports a 401 as an error and does nothing else: there is no sign-in to go to", async () => {
    const client = createApiClient({
      baseUrl: "/api",
      fetchImpl: reply(401, {
        code: "auth.unauthorized",
        message: "No staff identity",
        correlationId: "c",
      }),
    });

    await expect(client.get("/admin/tiers")).rejects.toMatchObject({
      status: 401,
      code: "auth.unauthorized",
    });
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
