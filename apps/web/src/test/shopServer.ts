import { json } from "./render";

/**
 * A stand-in for the catalog and cart endpoints that follows what `services/api` does, including the
 * decisions T-034 recorded: a repeat add is a **no-op**, a quantity above the cap is refused with 400
 * `quantity.above-cap` (never clamped), and buy-now touches no cart. It records each request.
 */
export interface ShopProduct {
  id: string;
  name: string;
  categoryId?: string;
  metal?: string;
  stock?: "IN_STOCK" | "ON_REQUEST" | "OUT_OF_STOCK";
  /** Absent means the product is on request. */
  price?: string;
}

export interface ShopOptions {
  products: ShopProduct[];
  categories?: { id: string; name: string }[];
  /** Overrides the related list of every product (by default: the others in its category). */
  related?: ShopProduct[];
  /** The cap the server enforces. */
  cap?: number;
  /** The cap the lines announce; differs from `cap` only in a test of the server's refusal. */
  announcedCap?: number;
}

const money = (amount: string) => ({ amount, currency: "EUR" });

function summary(product: ShopProduct) {
  const priced = product.price !== undefined;
  return {
    id: product.id,
    name: product.name,
    categoryId: product.categoryId ?? "bars",
    metal: product.metal ?? "GOLD",
    stock: product.stock ?? "IN_STOCK",
    pricingMode: priced ? "FIXED" : "ON_REQUEST",
    ...(priced ? { price: money(product.price ?? "0") } : {}),
  };
}

export function createShop(options: ShopOptions) {
  const cap = options.cap ?? 10;
  const announcedCap = options.announcedCap ?? cap;
  const categories = options.categories ?? [{ id: "bars", name: "Bars" }];
  const quantities = new Map<string, number>();
  const sent: { method: string; url: string; body: unknown }[] = [];
  const find = (id: string) => options.products.find((product) => product.id === id);

  function cart() {
    const lines = [...quantities].map(([id, quantity]) => {
      const product = find(id);
      const price = product?.price;
      const net = price === undefined ? undefined : (Number(price) * quantity).toFixed(2);
      return {
        productId: id,
        name: product?.name ?? id,
        quantity,
        maxQuantity: announcedCap,
        pricingMode: price === undefined ? "ON_REQUEST" : "FIXED",
        ...(price === undefined || net === undefined
          ? {}
          : {
              unitPrice: money(price),
              lineNet: money(net),
              taxRatePercent: "19.00",
              lineTax: money((Number(net) * 0.19).toFixed(2)),
            }),
      };
    });
    const net = lines.reduce((sum, line) => sum + Number(line.lineNet?.amount ?? 0), 0);
    return {
      ...(lines.length > 0 ? { cartId: "cart-1" } : {}),
      empty: lines.length === 0,
      itemCount: lines.reduce((sum, line) => sum + line.quantity, 0),
      complete: lines.every((line) => line.pricingMode === "FIXED"),
      lines,
      ...(net > 0
        ? {
            totals: {
              net: money(net.toFixed(2)),
              tax: money((net * 0.19).toFixed(2)),
              total: money((net * 1.19).toFixed(2)),
            },
          }
        : {}),
    };
  }

  const error = (status: number, code: string, message: string) =>
    Promise.resolve(json({ code, message, correlationId: "corr-1" }, status));

  const fetchImpl: typeof globalThis.fetch = (input, init) => {
    const target =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(target, "http://localhost");
    const method = init?.method ?? "GET";
    const body: unknown = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    sent.push({ method, url: url.pathname + url.search, body });
    const path = url.pathname.replace(/^\/api/, "");
    const reply = (value: unknown) => Promise.resolve(json(value));

    if (path === "/catalog/categories")
      return reply(categories.map((c) => ({ ...c, taxCategory: "STANDARD" })));
    let match = /^\/catalog\/categories\/([^/]+)\/products$/.exec(path);
    if (match) {
      const all = options.products.filter((p) => (p.categoryId ?? "bars") === match?.[1]);
      const page = Number(url.searchParams.get("page") ?? 0);
      const size = Number(url.searchParams.get("size") ?? 20);
      return reply({
        items: all.slice(page * size, (page + 1) * size).map(summary),
        page,
        size,
        total: all.length,
      });
    }
    match = /^\/catalog\/products\/([^/]+)\/related$/.exec(path);
    if (match) {
      const id = match[1];
      const others = options.related ?? options.products.filter((p) => p.id !== id);
      return reply(others.map(summary));
    }
    match = /^\/catalog\/products\/([^/]+)$/.exec(path);
    if (match) {
      const product = find(match[1] ?? "");
      if (!product) return error(404, "product.not-found", "No such product");
      return reply({
        ...summary(product),
        categoryName: "Bars",
        purity: "999.9",
        weight: { amount: "100", unit: "GRAM" },
        dimensions: "50 x 28 mm",
        tax: { category: "INVESTMENT_GRADE", ratePercent: "0" },
      });
    }
    if (path === "/catalog/search") {
      const q = (url.searchParams.get("q") ?? "").toLowerCase();
      return reply(options.products.filter((p) => p.name.toLowerCase().includes(q)).map(summary));
    }
    if (path === "/cart" && method === "GET") return reply(cart());
    if (path === "/cart/lines" && method === "POST") {
      const id = (body as { productId: string }).productId;
      const product = find(id);
      if (!product) return error(404, "product.not-found", "No such product");
      if (product.price === undefined) {
        return error(400, "cart.product-unpriced", "This product is sold on request");
      }
      if (!quantities.has(id)) quantities.set(id, 1);
      return reply(cart());
    }
    match = /^\/cart\/lines\/([^/]+)$/.exec(path);
    if (match && method === "PUT") {
      const id = match[1] ?? "";
      const quantity = (body as { quantity: number }).quantity;
      if (!quantities.has(id)) return error(404, "cart.line-not-found", "No such line");
      if (quantity > cap) {
        return error(400, "quantity.above-cap", `At most ${String(cap)} of a product`);
      }
      quantities.set(id, quantity);
      return reply(cart());
    }
    if (match && method === "DELETE") {
      quantities.delete(match[1] ?? "");
      return reply(cart());
    }
    if (path === "/cart/buy-now") {
      const product = find((body as { productId: string }).productId);
      if (product?.price === undefined) {
        return error(400, "cart.product-unpriced", "This product is sold on request");
      }
      return reply({ ...cart(), empty: false, itemCount: 1 });
    }
    return error(404, "http.404", "no");
  };

  return { fetchImpl, sent, quantities };
}
