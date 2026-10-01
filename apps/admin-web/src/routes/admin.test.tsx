import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import App from "../App";
import { createApp } from "../app/createApp";
import { createAdminServer, order, tier, type FakeOrder } from "../test/adminServer";
import type { TierView } from "../api/types";

/** Opens a page as a signed-in manager and waits until the server has said who that is. */
async function open(path: string, state: { tiers?: TierView[]; orders?: FakeOrder[] } = {}) {
  const server = createAdminServer(state);
  const app = createApp({ initialEntries: [path], baseUrl: "/api", fetchImpl: server.fetchImpl });
  app.tokenStore.set("token-for-manager");
  render(<App app={app} />);
  await screen.findByTestId("staff-identity");
  return { server, app };
}

const posts = (server: ReturnType<typeof createAdminServer>, suffix: string) =>
  server.sent.filter((r) => r.method !== "GET" && r.url.endsWith(suffix));

describe("the shell", () => {
  it("shows who the server says the user is, and sends the token with every call", async () => {
    const { server } = await open("/tiers");

    await waitFor(() => {
      expect(screen.getByTestId("staff-identity")).toHaveTextContent(
        "Signed in as manager (MANAGER)",
      );
    });
    await screen.findByRole("heading", { name: "Delivery tiers" });

    expect(server.sent.length).toBeGreaterThan(0);
    for (const request of server.sent) {
      expect(request.headers.get("Authorization")).toBe("Bearer token-for-manager");
    }
  });
});

describe("tiers", () => {
  const field = (label: string) => screen.getByLabelText(label);
  async function fill(values: Record<string, string>) {
    for (const [label, value] of Object.entries(values)) {
      await userEvent.clear(field(label));
      await userEvent.type(field(label), value);
    }
  }
  const VALID = {
    "Region (two-letter country code)": "de",
    "Value ceiling, before tax": "5000.00",
    "Weight ceiling": "2.5",
    "Delivery price, insurance included": "12.50",
    "Fewest days in transit": "2",
    "Most days in transit": "4",
  };

  it("renders the server's own message beside the ceilings when they are invalid", async () => {
    await open("/tiers/new");
    await fill({ ...VALID, "Value ceiling, before tax": "0" });
    await userEvent.click(screen.getByRole("button", { name: "Save tier" }));

    await waitFor(() => {
      expect(field("Value ceiling, before tax")).toHaveAccessibleDescription(
        "Tier ceilings must be present and greater than zero",
      );
    });
    expect(field("Weight ceiling")).toHaveAccessibleDescription(
      "Tier ceilings must be present and greater than zero",
    );
    expect(field("Delivery price, insurance included")).not.toHaveAccessibleDescription();
  });

  it("renders the server's message beside the price when it is invalid", async () => {
    await open("/tiers/new");
    await fill({ ...VALID, "Delivery price, insurance included": "-3" });
    await userEvent.click(screen.getByRole("button", { name: "Save tier" }));

    await waitFor(() => {
      expect(field("Delivery price, insurance included")).toHaveAccessibleDescription(
        "Tier delivery price must be present and not negative",
      );
    });
    expect(field("Value ceiling, before tax")).not.toHaveAccessibleDescription();
  });

  it("renders the server's message beside the transit days when they are invalid", async () => {
    await open("/tiers/new");
    await fill({ ...VALID, "Fewest days in transit": "6", "Most days in transit": "3" });
    await userEvent.click(screen.getByRole("button", { name: "Save tier" }));

    await waitFor(() => {
      expect(field("Most days in transit")).toHaveAccessibleDescription(
        "The most days must not be below the fewest",
      );
    });
    expect(field("Fewest days in transit")).toHaveAccessibleDescription(
      "The most days must not be below the fewest",
    );
  });

  it("creates a tier and converts the weight to the unit asked for", async () => {
    const { server } = await open("/tiers/new");
    await fill(VALID);
    await userEvent.click(screen.getByRole("button", { name: "Save tier" }));

    expect(await screen.findByText(/saved/)).toBeInTheDocument();
    expect(posts(server, "/tiers")[0]?.body).toMatchObject({
      region: "de",
      weightCeiling: "2.5",
      weightUnit: "KILOGRAM",
      currency: "EUR",
      minDays: 2,
      maxDays: 4,
    });
  });

  it("states the tie-break outcome for two overlapping tiers", async () => {
    await open("/tiers", {
      tiers: [
        tier({ id: "standard", region: "DE", deliveryPrice: "15.00" }),
        tier({ id: "economy", region: "DE", deliveryPrice: "9.00", maxDays: 7 }),
      ],
    });

    const overlaps = await screen.findByTestId("overlaps");

    expect(overlaps).toHaveTextContent(
      "Tiers economy and standard both accept orders up to 5000.00 EUR and 2500 g",
    );
    expect(overlaps).toHaveTextContent("economy is chosen (it is cheaper)");
  });

  it("shows the orders that fall between two tiers as uncovered", async () => {
    await open("/tiers", {
      tiers: [
        tier({ id: "rich", region: "DE", valueCeiling: "5000.00", weightGrams: "1000" }),
        tier({ id: "heavy", region: "DE", valueCeiling: "500.00", weightGrams: "10000" }),
      ],
    });

    const gaps = await screen.findByTestId("uncovered-bands");

    expect(gaps).toHaveTextContent(
      "Worth over 500.00 up to 5000.00 EUR: accepted only up to 1000 g",
    );
    expect(gaps).toHaveTextContent("they go to manager handoff");
    expect(screen.getByTestId("coverage-limits")).toHaveTextContent(
      "more than 5000.00 EUR or heavier than 10000 g",
    );
  });

  it("requires the warning to be ticked before the last tier of a region is deleted", async () => {
    const { server } = await open("/tiers", { tiers: [tier({ id: "only", region: "DE" })] });
    await userEvent.click(await screen.findByRole("button", { name: "Delete only" }));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("alert")).toHaveTextContent("the last tier of DE");
    expect(within(dialog).getByRole("button", { name: "Delete tier" })).toBeDisabled();
    expect(posts(server, "/tiers/only")).toHaveLength(0);

    await userEvent.click(within(dialog).getByRole("checkbox"));
    await userEvent.click(within(dialog).getByRole("button", { name: "Delete tier" }));

    const warnings = await screen.findByTestId("tier-warnings");
    expect(warnings).toHaveTextContent(
      "has no tier left: every order there will go to manager handoff",
    );
    expect(server.tiers()).toHaveLength(0);
  });

  it("asks before deleting any tier, and leaves it if cancelled", async () => {
    const { server } = await open("/tiers", {
      tiers: [
        tier({ id: "a", region: "DE" }),
        tier({ id: "b", region: "DE", deliveryPrice: "20.00" }),
      ],
    });
    await userEvent.click(await screen.findByRole("button", { name: "Delete a" }));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).queryByRole("checkbox")).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(server.tiers()).toHaveLength(2);
  });
});

describe("the quote queue and a quote", () => {
  const HANDED_OFF = order({ id: "q1", number: "100000000042", status: "AWAITING_MANAGER_QUOTE" });

  it("paginates the queue", async () => {
    const many = Array.from({ length: 25 }, (_, i) =>
      order({
        id: `q${String(i)}`,
        number: `10000000${String(100 + i)}`,
        status: "AWAITING_MANAGER_QUOTE",
      }),
    );
    const { server } = await open("/quotes", { orders: many });

    await screen.findAllByTestId("quote-row");
    expect(screen.getAllByTestId("quote-row")).toHaveLength(20);

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));

    await waitFor(() => {
      expect(screen.getAllByTestId("quote-row")).toHaveLength(5);
    });
    expect(server.sent.some((r) => r.url.includes("/quotes?page=1"))).toBe(true);
  });

  it("shows the full context on the detail screen", async () => {
    await open("/quotes/q1", { orders: [HANDED_OFF] });

    await screen.findByRole("heading", { name: "Quote for order 100000000042" });

    expect(screen.getByTestId("order-line")).toHaveTextContent("Gold bar 1 oz");
    expect(screen.getByTestId("destination")).toHaveTextContent("1 Main Street, 10115 Berlin, DE");
    expect(screen.getByTestId("region")).toHaveTextContent("DE");
    expect(screen.getByTestId("ex-tax-value")).toHaveTextContent("200.00 EUR");
    expect(screen.getByTestId("weight")).toHaveTextContent("3500 g");
    expect(screen.getByTestId("bound-ceiling")).toHaveTextContent(
      "weight is above the highest weight ceiling",
    );
    expect(screen.getByTestId("customer-name")).toHaveTextContent("Ann Example");
    expect(screen.getByTestId("customer-email")).toHaveTextContent("ann@example.com");
    expect(screen.getByTestId("customer-phone")).toHaveTextContent("+4930123456");
  });

  it("says plainly when the weight and ceiling could not be worked out", async () => {
    await open("/quotes/q1", { orders: [{ ...HANDED_OFF, weightGrams: null }] });

    expect(await screen.findByTestId("weight")).toHaveTextContent("Unknown");
    expect(screen.getByTestId("bound-ceiling")).toHaveTextContent("Unknown");
  });

  async function fillTerms(price: string) {
    await userEvent.type(screen.getByLabelText("Delivery price (EUR)"), price);
    await userEvent.type(screen.getByLabelText("Terms"), "Insured courier, signature required.");
    await userEvent.type(screen.getByLabelText("Fewest days in transit"), "3");
    await userEvent.type(screen.getByLabelText("Most days in transit"), "5");
    await userEvent.type(screen.getByLabelText("Offer valid until"), "2099-01-01T10:00");
  }

  it("shows the resulting total before anything is sent", async () => {
    const { server } = await open("/quotes/q1", { orders: [HANDED_OFF] });
    await screen.findByRole("heading", { name: "Set the terms" });

    expect(screen.getByTestId("total-preview")).toHaveTextContent("Enter a delivery price");
    await userEvent.type(screen.getByLabelText("Delivery price (EUR)"), "14.90");

    // net 200.00 + tax 38.00 + delivery 14.90, added exactly
    expect(screen.getByTestId("total-preview")).toHaveTextContent("252.90 EUR");
    expect(posts(server, "/terms")).toHaveLength(0);
  });

  it("confirms the total, then sets the terms and returns the order to awaiting payment", async () => {
    const { server } = await open("/quotes/q1", { orders: [HANDED_OFF] });
    await screen.findByRole("heading", { name: "Set the terms" });
    await fillTerms("14.90");

    await userEvent.click(screen.getByRole("button", { name: "Review and send terms" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("asked to pay 252.90 EUR");
    expect(posts(server, "/terms")).toHaveLength(0);
    await userEvent.click(within(dialog).getByRole("button", { name: "Send terms" }));

    expect(await screen.findByTestId("terms-outcome")).toHaveTextContent("now AWAITING_PAYMENT");
    expect(screen.getByTestId("outcome-total")).toHaveTextContent("252.90 EUR");
    expect(posts(server, "/terms")[0]?.body).toMatchObject({
      deliveryPrice: "14.90",
      terms: "Insured courier, signature required.",
      transitMinDays: 3,
      transitMaxDays: 5,
    });
  });

  it("sends nothing while a term is missing or nonsensical", async () => {
    const { server } = await open("/quotes/q1", { orders: [HANDED_OFF] });
    await screen.findByRole("heading", { name: "Set the terms" });
    await userEvent.type(screen.getByLabelText("Delivery price (EUR)"), "0");

    await userEvent.click(screen.getByRole("button", { name: "Review and send terms" }));

    expect(screen.getByLabelText("Delivery price (EUR)")).toHaveAccessibleDescription(
      "Enter a delivery price above zero",
    );
    expect(screen.getByLabelText("Terms")).toHaveAccessibleDescription(/Write the terms/);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(posts(server, "/terms")).toHaveLength(0);
  });

  it("requires a reason to decline, confirms, and cancels the order", async () => {
    const { server } = await open("/quotes/q1", { orders: [HANDED_OFF] });
    await screen.findByRole("heading", { name: "Decline the quote" });

    await userEvent.click(screen.getByRole("button", { name: "Decline the quote" }));
    expect(screen.getByLabelText("Reason")).toHaveAccessibleDescription(
      "Give a reason for declining",
    );
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await userEvent.type(screen.getByLabelText("Reason"), "We cannot ship to that address");
    await userEvent.click(screen.getByRole("button", { name: "Decline the quote" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("Order 100000000042 will be cancelled");
    expect(posts(server, "/decline")).toHaveLength(0);
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Decline and cancel the order" }),
    );

    expect(await screen.findByTestId("decline-outcome")).toHaveTextContent("now CANCELLED");
    expect(posts(server, "/decline")[0]?.body).toEqual({
      reason: "We cannot ship to that address",
    });
  });

  it("keeps the order when the decline confirmation is dismissed", async () => {
    const { server } = await open("/quotes/q1", { orders: [HANDED_OFF] });
    await screen.findByRole("heading", { name: "Decline the quote" });
    await userEvent.type(screen.getByLabelText("Reason"), "No");
    await userEvent.click(screen.getByRole("button", { name: "Decline the quote" }));

    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(posts(server, "/decline")).toHaveLength(0);
  });
});

describe("order management", () => {
  const button = (name: string) => screen.getByRole("button", { name });

  it("offers exactly what the server accepts, and shows the rest disabled", async () => {
    await open("/orders/o1", { orders: [order({ id: "o1", status: "PAID" })] });
    await screen.findByRole("heading", { name: /Order/ });

    expect(button("Start fulfilment")).toBeEnabled();
    expect(button("Mark as delivered")).toBeDisabled();
    expect(screen.queryByRole("form", { name: "Shipment" })).not.toBeInTheDocument();
  });

  it("follows the server's list, not the status: a status with other triggers gets those", async () => {
    await open("/orders/o1", {
      orders: [order({ id: "o1", status: "PAID", actions: ["DELIVERED"] })],
    });
    await screen.findByRole("heading", { name: /Order/ });

    expect(button("Mark as delivered")).toBeEnabled();
    expect(button("Start fulfilment")).toBeDisabled();
  });

  it("offers the shipment form only where shipping is legal, and needs both carrier and tracking", async () => {
    const { server } = await open("/orders/o1", {
      orders: [order({ id: "o1", status: "FULFILLING" })],
    });
    await screen.findByRole("form", { name: "Shipment" });

    await userEvent.type(screen.getByLabelText("Carrier"), "DHL");
    await userEvent.click(button("Mark as shipped"));
    expect(screen.getByLabelText("Tracking reference")).toHaveAccessibleDescription(
      "Enter the tracking reference",
    );
    expect(posts(server, "/shipment")).toHaveLength(0);

    await userEvent.clear(screen.getByLabelText("Carrier"));
    await userEvent.type(screen.getByLabelText("Tracking reference"), "TRACK-1");
    await userEvent.click(button("Mark as shipped"));
    expect(screen.getByLabelText("Carrier")).toHaveAccessibleDescription("Enter the carrier");
    expect(posts(server, "/shipment")).toHaveLength(0);

    await userEvent.type(screen.getByLabelText("Carrier"), "DHL");
    await userEvent.click(button("Mark as shipped"));

    await waitFor(() => {
      expect(screen.getByTestId("shipment")).toHaveTextContent("DHL");
    });
    expect(screen.getByTestId("shipment")).toHaveTextContent("TRACK-1");
    expect(posts(server, "/shipment")[0]?.body).toEqual({
      carrier: "DHL",
      trackingReference: "TRACK-1",
    });
    expect(screen.queryByRole("form", { name: "Shipment" })).not.toBeInTheDocument();
  });

  it("advances an order by the server's answer", async () => {
    await open("/orders/o1", { orders: [order({ id: "o1", status: "PAID" })] });
    await userEvent.click(await screen.findByRole("button", { name: "Start fulfilment" }));

    await waitFor(() => {
      expect(screen.getByTestId("order-status")).toHaveTextContent("Being fulfilled");
    });
    expect(await screen.findByRole("form", { name: "Shipment" })).toBeInTheDocument();
    expect(button("Start fulfilment")).toBeDisabled();
  });

  it("filters the list by status and paginates it", async () => {
    const orders = [
      ...Array.from({ length: 22 }, (_, i) =>
        order({ id: `p${String(i)}`, number: `20000000${String(100 + i)}`, status: "PAID" }),
      ),
      order({ id: "s1", number: "300000000001", status: "SHIPPED" }),
    ];
    const { server } = await open("/orders", { orders });
    await screen.findAllByTestId("order-row");
    expect(screen.getAllByTestId("order-row")).toHaveLength(20);

    await userEvent.click(screen.getByRole("button", { name: "Next page" }));
    await waitFor(() => {
      expect(screen.getAllByTestId("order-row")).toHaveLength(3);
    });

    await userEvent.selectOptions(screen.getByLabelText("Status"), "SHIPPED");
    await waitFor(() => {
      expect(screen.getAllByTestId("order-row")).toHaveLength(1);
    });
    expect(screen.getByTestId("order-row")).toHaveTextContent("300000000001");
    expect(server.sent.some((r) => r.url.includes("status=SHIPPED"))).toBe(true);
  });
});
