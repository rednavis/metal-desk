import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import App from "../App";
import { createApp } from "../app/createApp";
import { ErrorState } from "../ui";
import { ApiError } from "../api/errors";

function renderAt(path: string, fetchImpl?: typeof globalThis.fetch) {
  const app = createApp({ initialEntries: [path], baseUrl: "/api", fetchImpl });
  render(<App app={app} />);
  return app;
}

describe("the storefront's routes", () => {
  it("renders the home page", () => {
    renderAt("/");

    expect(screen.getByRole("heading", { name: "MetalDesk" })).toBeInTheDocument();
  });

  it("shows an explicit empty cart", () => {
    renderAt("/cart");

    expect(screen.getByRole("heading", { name: "Your cart is empty" })).toBeInTheDocument();
  });

  it("answers an unknown URL with a not-found page, not a blank one", () => {
    renderAt("/no/such/page");

    expect(screen.getByRole("heading", { name: "Page not found" })).toBeInTheDocument();
  });

  it("clears the token and routes to sign-in when the API answers 401", async () => {
    const unauthorized = vi.fn<typeof globalThis.fetch>(() =>
      Promise.resolve(
        new Response(
          JSON.stringify({ code: "auth.unauthorized", message: "Sign in", correlationId: "c" }),
          { status: 401 },
        ),
      ),
    );
    const app = renderAt("/checkout", unauthorized);
    app.tokenStore.set("stale-jwt");

    await expect(app.client.get("/orders")).rejects.toBeInstanceOf(ApiError);

    expect(app.tokenStore.get()).toBeNull();
    await waitFor(() => {
      expect(app.router.state.location.pathname).toBe("/sign-in");
    });
    expect(app.router.state.location.search).toBe("?from=%2Fcheckout");
    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
  });
});

describe("the error state", () => {
  it("shows the correlation id of a server error so it can be quoted to support", () => {
    render(<ErrorState error={new ApiError(500, "internal-error", "Try again later", "ref-42")} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Try again later");
    expect(screen.getByText("ref-42")).toBeInTheDocument();
  });

  it("does not invent a reference for an error that did not come from the server", () => {
    render(<ErrorState error={new Error("boom")} />);

    expect(screen.getByRole("alert")).not.toHaveTextContent("reference");
  });
});
