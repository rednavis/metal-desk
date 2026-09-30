import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ApiError } from "../api/errors";
import { createFakeServer } from "../test/fakeServer";
import { createShop } from "../test/shopServer";
import { json, memoryStorage, renderApp, renderWithPreferences } from "../test/render";
import { ErrorState } from "../ui";

describe("the storefront's routes", () => {
  it("renders the home page", () => {
    renderApp({});

    expect(screen.getByRole("heading", { name: "MetalDesk" })).toBeInTheDocument();
  });

  it("shows an explicit empty cart", async () => {
    renderApp({ path: "/cart", fetchImpl: createShop({ products: [] }).fetchImpl });

    expect(await screen.findByRole("heading", { name: "Your cart is empty" })).toBeInTheDocument();
  });

  it("answers an unknown URL with a not-found page, not a blank one", () => {
    renderApp({ path: "/no/such/page" });

    expect(screen.getByRole("heading", { name: "Page not found" })).toBeInTheDocument();
  });

  it("switches the whole page to German from the switcher, without a reload", async () => {
    const shop = createShop({ products: [] });
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });
    await screen.findByRole("heading", { name: "Your cart is empty" });

    await userEvent.selectOptions(screen.getByLabelText("Language"), "de");

    expect(
      await screen.findByRole("heading", { name: "Ihr Warenkorb ist leer" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Katalog" })).toBeInTheDocument();
    expect(screen.getByLabelText("Sprache")).toHaveValue("de");
  });

  it("says plainly that the rates are demo rates and checkout is in the settlement currency", async () => {
    renderApp({ fetchImpl: createFakeServer().fetchImpl });
    await screen.findByRole("option", { name: "USD" });

    await userEvent.selectOptions(screen.getByLabelText("Currency"), "USD");

    expect(screen.getByRole("note")).toHaveTextContent(
      "demo exchange rates, not real market rates",
    );
    expect(screen.getByRole("note")).toHaveTextContent("always in EUR");
  });

  it("clears the token and routes to sign-in when the API answers 401", async () => {
    const unauthorized = vi.fn<typeof globalThis.fetch>(() =>
      Promise.resolve(
        json({ code: "auth.unauthorized", message: "Sign in", correlationId: "c" }, 401),
      ),
    );
    const { app } = renderApp({ path: "/checkout", fetchImpl: unauthorized });
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
    renderWithPreferences(
      <ErrorState error={new ApiError(500, "internal-error", "Try again later", "ref-42")} />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent("Try again later");
    expect(screen.getByRole("alert")).toHaveTextContent("ref-42");
  });

  it("does not invent a reference for an error that did not come from the server", () => {
    renderWithPreferences(<ErrorState error={new Error("boom")} />);

    expect(screen.getByRole("alert")).not.toHaveTextContent("reference");
  });

  it("is translated", () => {
    const storage = memoryStorage();
    storage.setItem("metaldesk.preferences", JSON.stringify({ locale: "de" }));

    renderWithPreferences(<ErrorState error={new Error("boom")} />, { storage });

    expect(screen.getByRole("heading", { name: "Etwas ist schiefgelaufen" })).toBeInTheDocument();
  });
});
