import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { createShop, type ShopProduct } from "../../test/shopServer";
import { json, renderApp } from "../../test/render";

const BAR: ShopProduct = { id: "p1", name: "Gold Bar 100 g", price: "100.00" };
const COIN: ShopProduct = { id: "p2", name: "Silver Coin", price: "25.00" };
const UNPRICED: ShopProduct = { id: "p3", name: "Rhodium Ingot" };

const badge = () => screen.queryByTestId("cart-count")?.textContent ?? null;

describe("add to cart", () => {
  it("updates the badge in place, without navigating", async () => {
    const shop = createShop({ products: [BAR] });
    const { app } = renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });
    await screen.findByRole("heading", { name: "Gold Bar 100 g" });
    const before = app.router.state.location.pathname;

    await userEvent.click(screen.getByRole("button", { name: /Add to cart/ }));

    await waitFor(() => {
      expect(badge()).toBe("1");
    });
    expect(app.router.state.location.pathname).toBe(before);
    expect(screen.getByRole("heading", { name: "Gold Bar 100 g" })).toBeInTheDocument();
  });

  it("shows whatever the server did with a repeat add: a no-op, not a second unit", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });
    await screen.findByRole("heading", { name: "Gold Bar 100 g" });
    const add = screen.getByRole("button", { name: /Add to cart/ });

    await userEvent.click(add);
    await waitFor(() => {
      expect(badge()).toBe("1");
    });
    await userEvent.click(add);
    await waitFor(() => {
      expect(shop.sent.filter((r) => r.method === "POST")).toHaveLength(2);
    });

    expect(badge()).toBe("1");
    expect(shop.quantities.get("p1")).toBe(1);
  });

  it("renders the server's refusal rather than swallowing it", async () => {
    const shop = createShop({ products: [BAR] });
    const busy: typeof globalThis.fetch = (input, init) =>
      init?.method === "POST"
        ? Promise.resolve(
            json({ code: "cart.busy", message: "The cart is busy", correlationId: "c9" }, 409),
          )
        : shop.fetchImpl(input, init);
    renderApp({ path: "/catalog/products/p1", fetchImpl: busy });
    await screen.findByRole("heading", { name: "Gold Bar 100 g" });

    await userEvent.click(screen.getByRole("button", { name: /Add to cart/ }));

    expect(await screen.findByRole("alert")).toHaveTextContent("The cart is busy");
    expect(badge()).toBeNull();
  });
});

describe("the cart page", () => {
  it("shows the explicit empty state with a way back to the catalog", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });

    expect(await screen.findByRole("heading", { name: "Your cart is empty" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse the catalog" })).toHaveAttribute(
      "href",
      "/catalog",
    );
  });

  it("lists lines with quantity, unit price, line tax and totals", async () => {
    const shop = createShop({ products: [BAR, COIN] });
    shop.quantities.set("p1", 2);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });

    const line = await screen.findByTestId("cart-line");
    expect(within(line).getByRole("link", { name: "Gold Bar 100 g" })).toBeInTheDocument();
    expect(within(line).getByLabelText("Quantity of Gold Bar 100 g")).toHaveValue(2);
    expect(within(line).getByTestId("price")).toHaveTextContent("100.00");
    expect(within(line).getByText(/38.00/)).toBeInTheDocument();
    expect(screen.getByTestId("total")).toHaveTextContent("238.00");
  });

  it("prevents a quantity above the cap without sending it, and keeps what was typed", async () => {
    const shop = createShop({ products: [BAR] });
    shop.quantities.set("p1", 1);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });
    const field = await screen.findByLabelText("Quantity of Gold Bar 100 g");

    await userEvent.clear(field);
    await userEvent.type(field, "12");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));

    expect(screen.getByText("At most 10 of a product per order.")).toBeInTheDocument();
    expect(field).toHaveValue(12);
    expect(shop.sent.some((r) => r.method === "PUT")).toBe(false);
  });

  it("renders the server's 400 when the cap it enforces is lower than announced", async () => {
    const shop = createShop({ products: [BAR], cap: 5, announcedCap: 10 });
    shop.quantities.set("p1", 1);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });
    const field = await screen.findByLabelText("Quantity of Gold Bar 100 g");

    await userEvent.clear(field);
    await userEvent.type(field, "8");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));

    expect(await screen.findByText("At most 5 of a product")).toBeInTheDocument();
    expect(shop.quantities.get("p1")).toBe(1);
  });

  it("updates a quantity within the cap from the server's answer", async () => {
    const shop = createShop({ products: [BAR] });
    shop.quantities.set("p1", 1);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });
    const field = await screen.findByLabelText("Quantity of Gold Bar 100 g");

    await userEvent.clear(field);
    await userEvent.type(field, "4");
    await userEvent.click(screen.getByRole("button", { name: "Update" }));

    await waitFor(() => {
      expect(badge()).toBe("4");
    });
    expect(screen.getByTestId("total")).toHaveTextContent("476.00");
  });

  it("explains a cart that holds an unpriced line and does not offer checkout", async () => {
    const shop = createShop({ products: [BAR, UNPRICED] });
    shop.quantities.set("p1", 1);
    shop.quantities.set("p3", 1);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });

    expect(await screen.findByText(/Some items have no price/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Proceed to checkout" })).not.toBeInTheDocument();
  });
});

describe("removing a line", () => {
  async function openDialog() {
    const shop = createShop({ products: [BAR, COIN] });
    shop.quantities.set("p1", 1);
    shop.quantities.set("p2", 1);
    renderApp({ path: "/cart", fetchImpl: shop.fetchImpl });
    const remove = await screen.findByRole("button", { name: "Remove Gold Bar 100 g" });
    await userEvent.click(remove);
    return { shop, remove };
  }

  it("asks first, focusing the safe choice rather than the destructive one", async () => {
    await openDialog();

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "Keep in cart" })).toHaveFocus();
  });

  it("leaves the line when cancelled, and returns focus to the button that opened it", async () => {
    const { shop, remove } = await openDialog();

    await userEvent.click(screen.getByRole("button", { name: "Keep in cart" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getAllByTestId("cart-line")).toHaveLength(2);
    expect(shop.sent.some((r) => r.method === "DELETE")).toBe(false);
    expect(remove).toHaveFocus();
  });

  it("is dismissed by Escape", async () => {
    await openDialog();

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getAllByTestId("cart-line")).toHaveLength(2);
  });

  it("keeps focus inside while open, in both directions", async () => {
    await openDialog();
    const keep = screen.getByRole("button", { name: "Keep in cart" });
    const confirm = within(screen.getByRole("dialog")).getByRole("button", { name: "Remove" });

    await userEvent.tab();
    expect(confirm).toHaveFocus();
    await userEvent.tab();
    expect(keep).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(confirm).toHaveFocus();
  });

  it("removes the line once confirmed", async () => {
    const { shop } = await openDialog();

    await userEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: "Remove" }),
    );

    await waitFor(() => {
      expect(screen.getAllByTestId("cart-line")).toHaveLength(1);
    });
    expect(shop.quantities.has("p1")).toBe(false);
  });
});

describe("buy now", () => {
  it("opens checkout step 1 for that product and leaves the cart unchanged", async () => {
    const shop = createShop({ products: [BAR, COIN] });
    shop.quantities.set("p2", 1);
    const { app } = renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });
    await screen.findByRole("heading", { name: "Gold Bar 100 g" });

    await userEvent.click(screen.getByRole("button", { name: /Buy now/ }));

    await waitFor(() => {
      expect(app.router.state.location.pathname).toBe("/checkout");
    });
    expect(app.router.state.location.search).toBe("?buyNow=p1");
    expect(shop.quantities).toEqual(new Map([["p2", 1]]));
    expect(shop.sent.filter((r) => r.method !== "GET")).toEqual([]);
  });
});
