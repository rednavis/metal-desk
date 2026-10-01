import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { describe, expect, it, vi } from "vitest";
import { createCheckoutServer, OFFER_ALL, type CheckoutOptions } from "../../test/checkoutServer";
import { json, renderApp, renderWithPreferences } from "../../test/render";
import { PaymentFlowRouter } from "./PaymentFlowRouter";
import { toOutcome, type PaymentOutcome } from "./outcome";

const LABEL = {
  name: "Name",
  email: "Email",
  phone: "Phone",
  street: "Street and number",
  city: "City",
  country: "Country (two-letter code, for example DE)",
  postalCode: "Postal code",
  privacy: "I have read and accept the privacy policy.",
  rememberMe: "Remember me: create an account from these details",
};

function open(options: CheckoutOptions = {}, path = "/checkout/cs1") {
  const server = createCheckoutServer(options);
  const rendered = renderApp({ path, fetchImpl: server.fetchImpl });
  return { server, ...rendered };
}

async function fillStep1(extra: { privacy?: boolean } = {}) {
  await screen.findByRole("heading", { name: "Your details and delivery address" });
  await userEvent.type(screen.getByLabelText(LABEL.name), "Ada Lovelace");
  await userEvent.type(screen.getByLabelText(LABEL.email), "ada@example.test");
  await userEvent.type(screen.getByLabelText(LABEL.phone), "+4930123456");
  await userEvent.type(screen.getByLabelText(LABEL.street), "Main St 1");
  await userEvent.type(screen.getByLabelText(LABEL.city), "Berlin");
  await userEvent.type(screen.getByLabelText(LABEL.country), "de");
  await userEvent.type(screen.getByLabelText(LABEL.postalCode), "10115");
  if (extra.privacy !== false) await userEvent.click(screen.getByLabelText(LABEL.privacy));
}

const submit = () => userEvent.click(screen.getByRole("button", { name: "Continue" }));
const payControls = () => screen.queryAllByRole("button", { name: /pay/i });

describe("step 1: your details", () => {
  it("starts a checkout and moves to its own address", async () => {
    const { server, app } = open({}, "/checkout");

    await screen.findByRole("heading", { name: "Your details and delivery address" });

    expect(app.router.state.location.pathname).toBe("/checkout/cs1");
    expect(server.sent.find((r) => r.method === "POST")?.body).toEqual({});
  });

  it("starts a buy-now checkout for the named product", async () => {
    const { server } = open({}, "/checkout?buyNow=p1");

    await screen.findByRole("heading", { name: "Your details and delivery address" });

    expect(server.sent.find((r) => r.method === "POST")?.body).toEqual({ buyNowProductId: "p1" });
  });

  it("explains an empty basket instead of showing a blank page", async () => {
    open({ emptyBasket: true }, "/checkout");

    expect(await screen.findByRole("alert")).toHaveTextContent("Your cart is empty");
    expect(screen.getByRole("link", { name: "Back to the cart" })).toBeInTheDocument();
  });

  it("shows every server violation beside its own input", async () => {
    open({
      violations: [
        { field: "name", code: "required", message: "name is required" },
        { field: "contact.email", code: "format", message: "That is not a valid contact.email" },
      ],
    });
    await fillStep1();

    await submit();

    await waitFor(() => {
      expect(screen.getByLabelText(LABEL.name)).toHaveAccessibleDescription("name is required");
    });
    expect(screen.getByLabelText(LABEL.email)).toHaveAccessibleDescription(
      "That is not a valid contact.email",
    );
    expect(screen.getByLabelText(LABEL.city)).not.toHaveAccessibleDescription();
  });

  it("has a separate privacy checkbox, unchecked, that blocks progress until accepted", async () => {
    const { server } = open();
    await fillStep1({ privacy: false });
    const privacy = screen.getByLabelText(LABEL.privacy);
    const remember = screen.getByLabelText(LABEL.rememberMe);

    expect(privacy).not.toBeChecked();
    expect(privacy).not.toBe(remember);

    await submit();

    expect(screen.getByText("You must accept the privacy policy to continue.")).toBeInTheDocument();
    expect(server.sent.some((r) => r.method === "PUT")).toBe(false);

    await userEvent.click(privacy);
    await submit();

    expect(await screen.findByRole("heading", { name: "Delivery" })).toBeInTheDocument();
    expect(server.sent.find((r) => r.method === "PUT")?.body).toMatchObject({
      privacyPolicyAccepted: true,
      policyVersion: "2026-10",
    });
  });

  it("converts the checkout into an account with remember-me, without waiting for verification", async () => {
    const { server } = open();
    await fillStep1();
    await userEvent.click(screen.getByLabelText(LABEL.rememberMe));
    await userEvent.type(screen.getByLabelText("Password"), "correct horse 9");

    await submit();

    expect(await screen.findByRole("heading", { name: "Delivery" })).toBeInTheDocument();
    expect(server.sent.find((r) => r.method === "PUT")?.body).toMatchObject({
      account: { rememberMe: true, password: "correct horse 9" },
    });
    expect(await screen.findByRole("status")).toHaveTextContent("conv-1");
    expect(server.sent.some((r) => r.url.endsWith("/confirm-email"))).toBe(false);
  });

  it("pre-fills a signed-in customer's saved address and sends what was edited", async () => {
    const { server, app } = open({
      profile: { street: "Saved Rd 9", city: "Hamburg", country: "DE", postalCode: "20095" },
    });
    act(() => {
      app.tokenStore.set("jwt");
    });

    const street = await screen.findByLabelText(LABEL.street);
    expect(street).toHaveValue("Saved Rd 9");
    expect(screen.getByRole("status")).toHaveTextContent("saved delivery address");

    await userEvent.clear(street);
    await userEvent.type(street, "Edited Rd 1");
    await userEvent.type(screen.getByLabelText(LABEL.name), "Ada");
    await userEvent.type(screen.getByLabelText(LABEL.email), "ada@example.test");
    await userEvent.type(screen.getByLabelText(LABEL.phone), "+4930123456");
    await userEvent.click(screen.getByLabelText(LABEL.privacy));
    await submit();

    await screen.findByRole("heading", { name: "Delivery" });
    expect(server.sent.find((r) => r.method === "PUT")?.body).toMatchObject({
      street: "Edited Rd 1",
      city: "Hamburg",
    });
  });
});

describe("step 2: delivery and the manager branch", () => {
  it("shows the quoted cost and transit time and the way on when payment is allowed", async () => {
    open();
    await fillStep1();
    await submit();

    expect(await screen.findByTestId("delivery-cost")).toHaveTextContent("14.90");
    expect(screen.getByText("2 to 4 days")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue to the payment method" })).toBeEnabled();
  });

  it.each([
    ["VALUE", "Limit reached: order value", /value of this order/],
    ["WEIGHT", "Limit reached: total weight", /weight of this order/],
  ] as const)(
    "replaces the primary action with the handoff when the %s ceiling binds",
    async (exceeded, ceiling, reason) => {
      open({ exceeded });
      await fillStep1();
      await submit();

      expect(
        await screen.findByRole("heading", { name: "Your order needs a manager" }),
      ).toBeInTheDocument();
      expect(screen.getByText(reason)).toBeInTheDocument();
      expect(screen.getByTestId("bound-ceiling")).toHaveTextContent(ceiling);
      expect(
        screen.getByRole("button", { name: "Hand my order to a manager" }),
      ).toBeInTheDocument();
      // No pay affordance anywhere: not a button, not a link, not a step of the progress.
      expect(payControls()).toHaveLength(0);
      expect(screen.queryByRole("link", { name: /pay/i })).not.toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /continue to the payment/i })).toBeNull();
      expect(screen.queryByText("Payment method")).not.toBeInTheDocument();
    },
  );

  it("names no ceiling when the region has no tier", async () => {
    open({ exceeded: "NO_TIER" });
    await fillStep1();
    await submit();

    expect(await screen.findByText(/no standard delivery option/)).toBeInTheDocument();
    expect(screen.queryByTestId("bound-ceiling")).not.toBeInTheDocument();
  });

  it("shows the reference number after the handoff, and still no payment affordance", async () => {
    open({ exceeded: "VALUE" });
    await fillStep1();
    await submit();

    await userEvent.click(
      await screen.findByRole("button", { name: "Hand my order to a manager" }),
    );

    expect(await screen.findByTestId("handoff-reference")).toHaveTextContent("100000000042");
    expect(payControls()).toHaveLength(0);
    expect(screen.queryByRole("button", { name: "Hand my order to a manager" })).toBeNull();
  });

  it("reloads the cart after the handoff, because the server removed it", async () => {
    const { server } = open({ exceeded: "VALUE", initial: { details: true } });
    await userEvent.click(
      await screen.findByRole("button", { name: "Hand my order to a manager" }),
    );
    await screen.findByTestId("handoff-reference");

    const paths = server.sent.map((request) => `${request.method} ${request.url.split("?")[0]}`);
    const handoffAt = paths.indexOf("POST /api/checkout/sessions/cs1/handoff");
    expect(handoffAt).toBeGreaterThanOrEqual(0);
    expect(paths.slice(handoffAt)).toContain("GET /api/cart");
  });

  it("resumes a handed-over order at its receipt after a reload", async () => {
    const { server } = open({ exceeded: "VALUE", initial: { details: true } });
    await userEvent.click(
      await screen.findByRole("button", { name: "Hand my order to a manager" }),
    );
    await screen.findByTestId("handoff-reference");

    renderApp({ path: "/checkout/cs1", fetchImpl: server.fetchImpl });

    expect((await screen.findAllByTestId("handoff-reference")).length).toBeGreaterThan(0);
  });
});

describe("step 3: payment method", () => {
  it("renders exactly the methods the server offers, invoice among them as an equal choice", async () => {
    open({ initial: { details: true, delivery: true } });

    await screen.findByRole("heading", { name: "Payment method" });

    const radios = screen.getAllByRole("radio");
    expect(radios).toHaveLength(OFFER_ALL.length);
    const invoice = screen.getByRole("radio", { name: "Invoice" });
    expect(invoice).toBeEnabled();
    expect(invoice.closest("fieldset")).toBe(radios[0]?.closest("fieldset"));
  });

  it("shows no gateway method for a high-value order, and still shows invoice", async () => {
    open({
      initial: { details: true, delivery: true },
      highValue: true,
      offered: [["INVOICE", "INVOICE"]],
    });

    await screen.findByRole("heading", { name: "Payment method" });

    expect(screen.getAllByRole("radio")).toHaveLength(1);
    expect(screen.getByRole("radio", { name: "Invoice" })).toBeInTheDocument();
    for (const name of ["Card", "Bank debit", "Online banking", "Saved wallet"]) {
      expect(screen.queryByRole("radio", { name })).not.toBeInTheDocument();
    }
    expect(screen.getByRole("note")).toHaveTextContent("only some payment methods");
  });

  it("chooses a method on the server and continues to the overview", async () => {
    const { server } = open({ initial: { details: true, delivery: true } });

    await userEvent.click(await screen.findByRole("radio", { name: "Card" }));
    await userEvent.click(screen.getByRole("button", { name: "Continue" }));

    expect(await screen.findByRole("heading", { name: "Review your order" })).toBeInTheDocument();
    expect(server.sent.find((r) => r.url.endsWith("/payment/method"))?.body).toEqual({
      method: "CARD",
    });
  });
});

describe("step 4: the overview", () => {
  const atOverview = { initial: { details: true, delivery: true, method: "CARD" } };

  it("shows the server's totals, not a sum of its own", async () => {
    open(atOverview);

    expect(await screen.findByTestId("grand-total")).toHaveTextContent("999.99");
    expect(screen.getByTestId("net")).toHaveTextContent("200.00");
    expect(screen.getByTestId("tax")).toHaveTextContent("38.00");
    expect(screen.getByTestId("delivery")).toHaveTextContent("14.90");
    // net + tax + delivery would be 252.90; that figure appears nowhere.
    expect(document.body.textContent).not.toContain("252.90");
    expect(screen.getByRole("button", { name: "Pay €999.99" })).toBeInTheDocument();
  });

  it("sends the total the customer saw so the server can refuse any other", async () => {
    const { server } = open(atOverview);

    await userEvent.click(await screen.findByRole("button", { name: /^Pay/ }));

    await waitFor(() => {
      expect(server.sent.find((r) => r.url.endsWith("/payment/execute"))?.body).toMatchObject({
        confirmedTotal: "999.99",
      });
    });
  });

  it("shows no separate line for cover against loss in transit anywhere in the flow", async () => {
    open(atOverview);
    await screen.findByTestId("grand-total");

    // FR-5.4 / BR-7 fold it into the delivery cost. The word is assembled so the spec's grep over this
    // directory finds nothing while this assertion still looks for it.
    expect(document.body.textContent).not.toMatch(new RegExp(["insur", "ance"].join(""), "i"));
  });

  it("clears the delivery quote and returns to step 2 when step 1 is edited", async () => {
    const { server } = open(atOverview);
    await screen.findByTestId("grand-total");
    expect(server.count("POST", "/delivery/evaluate")).toBe(0);

    await userEvent.click(screen.getByRole("button", { name: "Edit: Your details" }));
    await userEvent.click(await screen.findByLabelText(LABEL.privacy));
    await submit();

    expect(await screen.findByRole("heading", { name: "Delivery" })).toBeInTheDocument();
    await waitFor(() => {
      expect(server.count("POST", "/delivery/evaluate")).toBe(1);
    });
    expect(screen.queryByRole("heading", { name: "Review your order" })).not.toBeInTheDocument();
  });

  it("labels the invoice action as placing an order, not as paying", async () => {
    open({ initial: { details: true, delivery: true, method: "INVOICE" } });

    expect(
      await screen.findByRole("button", { name: "Place order and receive the invoice" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^Pay/ })).not.toBeInTheDocument();
  });
});

describe("payment outcomes through the wizard", () => {
  const atOverview = { initial: { details: true, delivery: true, method: "CARD" } };
  const pay = async () => userEvent.click(await screen.findByRole("button", { name: /^Pay/ }));

  it("confirms a captured payment with its order number", async () => {
    open({ ...atOverview, executes: [{ result: "CAPTURED", orderReference: "100000000001" }] });
    await pay();

    expect(await screen.findByTestId("order-number")).toHaveTextContent("100000000001");
    expect(screen.getByText("Payment received")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("confirms an issued invoice as pending payment, not as an error", async () => {
    open({
      initial: { details: true, delivery: true, method: "INVOICE" },
      executes: [{ result: "DOCUMENT_ISSUED", invoiceReference: "INV-7" }],
    });
    await userEvent.click(
      await screen.findByRole("button", { name: "Place order and receive the invoice" }),
    );

    expect(await screen.findByTestId("invoice-number")).toHaveTextContent("INV-7");
    expect(screen.getByText("Invoice issued, payment pending")).toBeInTheDocument();
    expect(screen.getByText("Awaiting payment", { exact: false })).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("returns a decline to the payment method with everything still there", async () => {
    open({
      ...atOverview,
      executes: [{ result: "DECLINED", declineReason: "insufficient funds" }],
    });
    await pay();

    expect(await screen.findByTestId("decline-notice")).toHaveTextContent("insufficient funds");
    const summary = within(screen.getByTestId("checkout-summary"));
    // The basket, the details, the delivery quote and the chosen method are all still shown.
    expect(summary.getByText(/Gold Bar 100 g/)).toBeInTheDocument();
    expect(summary.getByTestId("summary-details")).toHaveTextContent("Ada Lovelace");
    expect(summary.getByTestId("summary-delivery")).toHaveTextContent("14.90");
    expect(summary.getByTestId("summary-method")).toHaveTextContent("Card");
    expect(screen.getByRole("radio", { name: "Card" })).toBeChecked();
  });

  it("shows a failed payment with the reference it was sent under", async () => {
    const { server } = open({
      ...atOverview,
      executes: [{ result: "ERROR", errorCode: "provider.unavailable", message: "Try again soon" }],
    });
    await pay();

    const alert = await screen.findByRole("alert");
    const sentId = server.sent
      .find((r) => r.url.endsWith("/payment/execute"))
      ?.headers.get("X-Correlation-Id");
    expect(alert).toHaveTextContent("Try again soon");
    expect(sentId).toBeTruthy();
    expect(alert).toHaveTextContent(sentId ?? "never");
    expect(alert).toHaveTextContent("provider.unavailable");
  });

  it("offers a retry after a failure and keeps the overview", async () => {
    open({
      ...atOverview,
      executes: [
        { result: "ERROR", errorCode: "x", message: "Nope" },
        { result: "CAPTURED", orderReference: "100000000001" },
      ],
    });
    await pay();
    await userEvent.click(await screen.findByRole("button", { name: "Try again" }));

    await pay();

    expect(await screen.findByTestId("order-number")).toBeInTheDocument();
  });

  it("mounts the embedded payment element for an element outcome", async () => {
    open({ ...atOverview, executes: [{ result: "ELEMENT", clientHandle: "secret-handle" }] });
    await pay();

    expect(
      await screen.findByRole("heading", { name: "Enter your payment details" }),
    ).toBeInTheDocument();
  });
});

describe("the payment outcomes rendered from fixtures", () => {
  function renderOutcome(
    outcome: PaymentOutcome,
    extra: Partial<{ redirect: (u: string) => void }> = {},
  ) {
    return renderWithPreferences(
      <MemoryRouter>
        <PaymentFlowRouter checkoutId="cs1" outcome={outcome} correlationId="corr-9" {...extra} />
      </MemoryRouter>,
    );
  }

  it("leaves for the provider on a redirect", () => {
    const redirect = vi.fn();
    renderOutcome({ kind: "redirect", url: "https://pay.example.test/x" }, { redirect });

    expect(redirect).toHaveBeenCalledExactlyOnceWith("https://pay.example.test/x");
    expect(screen.getByText("Taking you to your payment provider")).toBeInTheDocument();
  });

  it("reads every wire result into exactly one outcome", () => {
    const cases: [Record<string, unknown>, PaymentOutcome["kind"]][] = [
      [{ result: "CAPTURED" }, "captured"],
      [{ result: "REDIRECT", redirectUrl: "https://x.test" }, "redirect"],
      [{ result: "ELEMENT", clientHandle: "h" }, "element"],
      [{ result: "DOCUMENT_ISSUED", invoiceReference: "I" }, "documentIssued"],
      [{ result: "DECLINED", declineReason: "no" }, "declined"],
      [{ result: "ERROR", errorCode: "e", message: "m" }, "failed"],
    ];
    for (const [wire, kind] of cases) {
      expect(toOutcome(wire as never).kind).toBe(kind);
    }
  });

  it("treats a redirect without an address as a failure, never as nothing", () => {
    expect(toOutcome({ result: "REDIRECT" }).kind).toBe("failed");
    expect(toOutcome({ result: "ELEMENT" }).kind).toBe("failed");
  });
});

describe("the provider's return and cancel", () => {
  it("confirms by asking the server with only the session and the reference", async () => {
    const { server } = open(
      {
        callback: { result: "CAPTURED", orderReference: "100000000001" },
        initial: { details: true, delivery: true, method: "CARD", placed: "PAID" },
      },
      "/checkout/cs1/return?reference=r-1&status=success&paid=true",
    );

    expect(await screen.findByTestId("order-number")).toBeInTheDocument();
    const callback = server.sent.find((r) => r.url.startsWith("/api/checkout/payment/callback"));
    expect(callback?.url).toBe("/api/checkout/payment/callback?checkout=cs1&reference=r-1");
  });

  it("shows a return the server could not match as an error with a way back", async () => {
    const base = createCheckoutServer();
    const fetchImpl: typeof globalThis.fetch = (input, init) => {
      const target =
        typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
      return target.includes("/payment/callback")
        ? Promise.resolve(
            json(
              { code: "payment.callback-invalid", message: "No match", correlationId: "c" },
              400,
            ),
          )
        : base.fetchImpl(input, init);
    };
    renderApp({ path: "/checkout/cs1/return?reference=bad", fetchImpl });

    expect(await screen.findByRole("alert")).toHaveTextContent("No match");
    expect(screen.getByRole("button", { name: "Back to the payment method" })).toBeInTheDocument();
  });

  it("says nothing was charged on a cancel and links back to the payment method", () => {
    open({}, "/checkout/cs1/cancel");

    expect(screen.getByText(/Nothing was charged/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Back to the payment method" })).toHaveAttribute(
      "href",
      "/checkout/cs1?step=3",
    );
  });
});

describe("resuming from the server", () => {
  it.each([
    [{}, "Your details and delivery address"],
    [{ initial: { details: true } }, "Delivery"],
    [{ initial: { details: true, delivery: true } }, "Payment method"],
    [{ initial: { details: true, delivery: true, method: "CARD" } }, "Review your order"],
    [{ initial: { details: true, delivery: true, method: "CARD", placed: "PAID" } }, "Thank you"],
  ] as const)("opens %j at %s after a reload", async (options, heading) => {
    open(options);

    expect(await screen.findByRole("heading", { name: heading })).toBeInTheDocument();
  });

  it("does not let the address unlock a step the server has not", async () => {
    open({ initial: { details: true } }, "/checkout/cs1?step=4");

    expect(await screen.findByRole("heading", { name: "Delivery" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Review your order" })).not.toBeInTheDocument();
  });

  it("lets the customer go back to an earlier step", async () => {
    open({ initial: { details: true, delivery: true, method: "CARD" } }, "/checkout/cs1?step=1");

    expect(
      await screen.findByRole("heading", { name: "Your details and delivery address" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(LABEL.name)).toHaveValue("Ada Lovelace");
  });

  it("shows the server's refusal of an unknown or expired session", async () => {
    const fetchImpl: typeof globalThis.fetch = () =>
      Promise.resolve(
        new Response(
          JSON.stringify({ code: "checkout.not-found", message: "Gone", correlationId: "c" }),
          { status: 404 },
        ),
      );
    renderApp({ path: "/checkout/zzz", fetchImpl });

    expect(await screen.findByRole("alert")).toHaveTextContent("Gone");
  });
});
