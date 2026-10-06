import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { RELATED_CAP } from "../../features/catalog/limits";
import { createShop, type ShopProduct } from "../../test/shopServer";
import { renderApp } from "../../test/render";

const BAR: ShopProduct = { id: "p1", name: "Gold Bar 100 g", price: "6300.00" };
const COIN: ShopProduct = { id: "p2", name: "Silver Coin", metal: "SILVER", price: "25.00" };
const UNPRICED: ShopProduct = { id: "p3", name: "Rhodium Ingot", metal: "RHODIUM" };
const SOLD_OUT: ShopProduct = {
  id: "p4",
  name: "Platinum Bar",
  stock: "OUT_OF_STOCK",
  price: "3000.00",
};

function card(name: string) {
  const link = screen.getByRole("link", { name });
  const found = link.closest("[data-testid=product-card]");
  if (!(found instanceof HTMLElement)) throw new Error(`no card for ${name}`);
  return within(found);
}

describe("the catalog", () => {
  it("groups products by category and shows a price or the request affordance", async () => {
    const shop = createShop({ products: [BAR, UNPRICED] });
    renderApp({ path: "/catalog", fetchImpl: shop.fetchImpl });

    expect(await screen.findByRole("heading", { name: "Bars" })).toBeInTheDocument();
    expect(await screen.findByText("Gold Bar 100 g")).toBeInTheDocument();

    expect(card("Gold Bar 100 g").getByTestId("price")).toHaveTextContent("6,300.00");
    const unpriced = card("Rhodium Ingot");
    expect(unpriced.getByText("Price on request")).toBeInTheDocument();
    expect(unpriced.getByRole("link", { name: "Request a price" })).toBeInTheDocument();
    expect(unpriced.queryByTestId("price")).not.toBeInTheDocument();
  });

  it("offers neither add-to-cart nor buy-now for an unpriced product", async () => {
    const shop = createShop({ products: [UNPRICED] });
    renderApp({ path: "/catalog/products/p3", fetchImpl: shop.fetchImpl });

    await screen.findByRole("heading", { name: "Rhodium Ingot" });

    expect(screen.getByTestId("price-on-request")).toBeInTheDocument();
    expect(screen.queryByTestId("price")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add to cart/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Buy now/ })).not.toBeInTheDocument();
  });

  it("renders from pricingMode, not from the amount: a zero price on a fixed product is a price", async () => {
    const shop = createShop({ products: [{ id: "free", name: "Free Sample", price: "0.00" }] });
    renderApp({ path: "/catalog/products/free", fetchImpl: shop.fetchImpl });

    await screen.findByRole("heading", { name: "Free Sample" });

    expect(screen.getByTestId("price")).toHaveTextContent("0.00");
    expect(screen.queryByTestId("price-on-request")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Add to cart/ })).toBeInTheDocument();
  });

  it("offers no add-to-cart for an out-of-stock product, on the card or the detail page", async () => {
    const shop = createShop({ products: [SOLD_OUT] });
    const { unmount } = renderApp({ path: "/catalog", fetchImpl: shop.fetchImpl });

    await screen.findByText("Platinum Bar");
    expect(card("Platinum Bar").getByText("Out of stock")).toBeInTheDocument();
    expect(card("Platinum Bar").getByTestId("price")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add to cart/ })).not.toBeInTheDocument();
    unmount();

    renderApp({ path: "/catalog/products/p4", fetchImpl: shop.fetchImpl });
    await screen.findByRole("heading", { name: "Platinum Bar" });
    expect(screen.getAllByText("Out of stock").length).toBeGreaterThan(0);
    expect(screen.queryByRole("button", { name: /Add to cart/ })).not.toBeInTheDocument();
  });

  it("shows no price and no purchase buttons for a product whose stock is on request", async () => {
    const onRequest: ShopProduct = {
      id: "p5",
      name: "Palladium Bar",
      stock: "ON_REQUEST",
      price: "900.00",
    };
    const shop = createShop({ products: [onRequest] });
    renderApp({ path: "/catalog", fetchImpl: shop.fetchImpl });

    await screen.findByText("Palladium Bar");
    expect(card("Palladium Bar").queryByTestId("price")).not.toBeInTheDocument();
    expect(card("Palladium Bar").getByTestId("price-on-request")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Add to cart|Buy now/ })).not.toBeInTheDocument();
  });

  it("pages a category instead of requesting all of it", async () => {
    const products = Array.from({ length: 30 }, (_, i) => ({
      id: `n${String(i)}`,
      name: `Item ${String(i).padStart(2, "0")}`,
      price: "1.00",
    }));
    const shop = createShop({ products });
    renderApp({ path: "/catalog/categories/bars", fetchImpl: shop.fetchImpl });

    await screen.findByText("Item 00");
    expect(screen.getAllByTestId("product-card")).toHaveLength(12);
    expect(shop.sent.some((r) => r.url.includes("page=0&size=12"))).toBe(true);

    await userEvent.click(screen.getByRole("link", { name: "Next page" }));

    expect(await screen.findByText("Item 12")).toBeInTheDocument();
    expect(screen.getByText("Page 2 of 3")).toBeInTheDocument();
  });
});

describe("the product page", () => {
  it("shows the full specification", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });

    await screen.findByRole("heading", { name: "Gold Bar 100 g" });

    expect(screen.getByText("999.9")).toBeInTheDocument();
    expect(screen.getByText("100 g")).toBeInTheDocument();
    expect(screen.getByText("50 x 28 mm")).toBeInTheDocument();
    expect(screen.getByText("In stock")).toBeInTheDocument();
    expect(screen.getByTestId("price")).toHaveTextContent("6,300.00");
    expect(screen.getByText(/Investment-grade metal, tax rate/)).toBeInTheDocument();
  });

  it("puts the price, tax and purchase actions together in the buy box, under a breadcrumb", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });

    await screen.findByRole("heading", { name: "Gold Bar 100 g" });
    const box = within(screen.getByRole("complementary", { name: "Buy this product" }));

    expect(box.getByTestId("price")).toHaveTextContent("6,300.00");
    expect(box.getByText(/Investment-grade metal, tax rate/)).toBeInTheDocument();
    expect(box.getByText("In stock")).toBeInTheDocument();
    expect(box.getByRole("button", { name: /Add to cart/ })).toBeInTheDocument();
    expect(box.getByRole("link", { name: "Ask us about this product" })).toBeInTheDocument();
    const crumbs = within(screen.getByRole("navigation", { name: "Breadcrumb" }));
    expect(crumbs.getByRole("link", { name: "Catalog" })).toHaveAttribute("href", "/catalog");
  });

  it("never shows more related products than the cap", async () => {
    const related = Array.from({ length: RELATED_CAP + 5 }, (_, i) => ({
      id: `r${String(i)}`,
      name: `Related ${String(i)}`,
      price: "1.00",
    }));
    const shop = createShop({ products: [BAR], related });
    renderApp({ path: "/catalog/products/p1", fetchImpl: shop.fetchImpl });

    const heading = await screen.findByRole("heading", { name: "Related products" });
    const section = within(heading.closest("section") as HTMLElement);

    expect(section.getAllByTestId("product-card")).toHaveLength(RELATED_CAP);
  });

  it("shows exactly what arrives when there are fewer, and nothing when there are none", async () => {
    const few = createShop({ products: [BAR], related: [COIN, UNPRICED] });
    const { unmount } = renderApp({ path: "/catalog/products/p1", fetchImpl: few.fetchImpl });
    const heading = await screen.findByRole("heading", { name: "Related products" });
    expect(
      within(heading.closest("section") as HTMLElement).getAllByTestId("product-card"),
    ).toHaveLength(2);
    unmount();

    const none = createShop({ products: [BAR], related: [] });
    renderApp({ path: "/catalog/products/p1", fetchImpl: none.fetchImpl });
    await screen.findByRole("heading", { name: "Gold Bar 100 g" });
    await screen.findByText("In stock");
    expect(screen.queryByRole("heading", { name: "Related products" })).not.toBeInTheDocument();
  });

  it("renders the server's refusal of an unknown product", async () => {
    const shop = createShop({ products: [] });
    renderApp({ path: "/catalog/products/missing", fetchImpl: shop.fetchImpl });

    expect(await screen.findByRole("alert")).toHaveTextContent("No such product");
  });
});

describe("search", () => {
  it("opens the selected product's page from the results", async () => {
    const shop = createShop({ products: [BAR, COIN] });
    renderApp({ path: "/", fetchImpl: shop.fetchImpl });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search products" }), "coin{Enter}");
    expect(await screen.findByText("1 product found for “coin”")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("link", { name: "Silver Coin" }));

    expect(await screen.findByRole("heading", { name: "Silver Coin" })).toBeInTheDocument();
  });

  it("renders an empty state, not an error, when nothing matches", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/search?q=zzz", fetchImpl: shop.fetchImpl });

    expect(await screen.findByRole("heading", { name: "No products found" })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says it matches names alphabetically and does not claim relevance ranking", () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/search?q=gold", fetchImpl: shop.fetchImpl });

    expect(screen.getByText(/Results are not ranked by relevance/)).toBeInTheDocument();
  });

  it("refuses a too-short query before asking the server", async () => {
    const shop = createShop({ products: [BAR] });
    renderApp({ path: "/", fetchImpl: shop.fetchImpl });

    await userEvent.type(screen.getByRole("searchbox", { name: "Search products" }), "g{Enter}");

    expect(screen.getByText("Enter at least 2 characters.")).toBeInTheDocument();
    expect(shop.sent.some((r) => r.url.startsWith("/api/catalog/search"))).toBe(false);
  });
});
