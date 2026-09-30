import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { orderStatusSchema } from "../../api/types";
import { ADA, BOB, createAccountServer, type AccountOptions } from "../../test/accountServer";
import { renderApp, renderWithPreferences } from "../../test/render";
import { createShop } from "../../test/shopServer";
import { OrderStatusBadge } from "../orders/OrderStatusBadge";

function open(path: string, options: AccountOptions = {}) {
  const server = createAccountServer(options);
  const rendered = renderApp({ path, fetchImpl: server.fetchImpl });
  return { server, ...rendered };
}

async function signInAs(
  account: { email: string; password: string },
  path = "/sign-in",
  options: AccountOptions = {},
) {
  const opened = open(path, options);
  await fillAndSubmit(account.email, account.password);
  return opened;
}

async function fillAndSubmit(identifier: string, password: string) {
  await userEvent.type(screen.getByLabelText("Email or phone"), identifier);
  await userEvent.type(screen.getByLabelText("Password"), password);
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("sign in", () => {
  it("renders one identical message for an unknown identifier and a wrong password", async () => {
    const { server } = open("/sign-in");
    await fillAndSubmit("nobody@example.test", "whatever-1");
    const unknown = (await screen.findByRole("alert")).textContent;

    await userEvent.clear(screen.getByLabelText("Email or phone"));
    await userEvent.clear(screen.getByLabelText("Password"));
    await fillAndSubmit(ADA.email, "wrong-password");
    await waitFor(() => {
      expect(server.sent.filter((r) => r.url.endsWith("/auth/sign-in"))).toHaveLength(2);
    });
    const wrong = screen.getByRole("alert").textContent;

    expect(unknown).toBe("Sign-in failed. Check your details and try again.");
    expect(wrong).toBe(unknown);
  });

  it("shows neither the server's words nor a hint to register in the failure", async () => {
    open("/sign-in");
    await fillAndSubmit("nobody@example.test", "whatever-1");

    const alert = await screen.findByRole("alert");

    expect(alert).not.toHaveTextContent(/invalid credentials/i);
    expect(alert).not.toHaveTextContent(/register|account|password/i);
  });

  it("asks the server nothing before the form is submitted", async () => {
    const { server } = open("/sign-in");
    await userEvent.type(screen.getByLabelText("Email or phone"), "someone@example.test");

    expect(
      server.sent.filter((r) => r.url.includes("/auth") || r.url.includes("/account")),
    ).toEqual([]);
  });

  it("says to wait when throttled, without saying anything about the account", async () => {
    open("/sign-in", { throttleAfter: 1 });
    await fillAndSubmit(ADA.email, "wrong-1");
    await screen.findByRole("alert");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("Wait a while before trying again");
    });
    expect(screen.getByRole("alert")).not.toHaveTextContent(ADA.email);
    expect(screen.getByRole("alert")).not.toHaveTextContent(/exist|account|unknown/i);
  });

  it("stores the token and returns to the page that asked for the sign-in", async () => {
    const { app } = await signInAs(ADA, "/sign-in?from=%2Forders");

    expect(await screen.findByRole("heading", { name: "Your orders" })).toBeInTheDocument();
    expect(app.tokenStore.get()).toBe("tok-c-ada");
    expect(app.router.state.location.pathname).toBe("/orders");
  });

  it("does not send the customer to another site or another origin", async () => {
    const { app } = await signInAs(ADA, "/sign-in?from=%2F%2Fevil.example");

    await waitFor(() => {
      expect(app.router.state.location.pathname).toBe("/");
    });
  });
});

describe("the route guard", () => {
  it("sends an unauthenticated visitor to sign-in, remembering where they were going, and shows nothing protected", async () => {
    const { app } = open("/orders/100000000001");

    expect(screen.queryByRole("heading", { name: /Order 100000000001/ })).not.toBeInTheDocument();
    await screen.findByRole("heading", { name: "Sign in" });
    expect(app.router.state.location.pathname).toBe("/sign-in");
    expect(app.router.state.location.search).toBe("?from=%2Forders%2F100000000001");
  });

  it("shows a spinner and none of the page until the server has identified the token", async () => {
    let release: () => void = () => undefined;
    const holdMe = new Promise<void>((resolve) => {
      release = resolve;
    });
    const { app } = open("/orders", { holdMe });
    act(() => {
      app.tokenStore.set("tok-c-ada");
    });

    await waitFor(() => {
      expect(screen.getByRole("status")).toBeInTheDocument();
    });
    expect(screen.queryByRole("heading", { name: "Your orders" })).not.toBeInTheDocument();
    expect(screen.queryByTestId("order-row")).not.toBeInTheDocument();

    release();

    expect(await screen.findByRole("heading", { name: "Your orders" })).toBeInTheDocument();
  });

  it("routes an expired token to sign-in with the destination kept", async () => {
    const { app } = open("/orders", { expired: true });
    act(() => {
      app.tokenStore.set("tok-c-ada");
    });

    await screen.findByRole("heading", { name: "Sign in" });

    expect(app.tokenStore.get()).toBeNull();
    expect(app.router.state.location.search).toBe("?from=%2Forders");
  });
});

describe("registration and verification", () => {
  it("makes no existence or availability request, and explains the verification gate", async () => {
    const { server } = open("/register");
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.type(screen.getByLabelText("Email"), ADA.email);
    await userEvent.tab();
    await userEvent.type(screen.getByLabelText("Phone"), "+4930123456");
    await userEvent.type(screen.getByLabelText("Password"), "correct horse 9");

    const accountCalls = () => server.sent.filter((r) => r.url.startsWith("/api/account"));
    expect(accountCalls()).toEqual([]);

    await userEvent.click(screen.getByRole("button", { name: "Register" }));

    expect(await screen.findByRole("heading", { name: "Check your email" })).toBeInTheDocument();
    expect(
      screen.getByText(/You must confirm your address before you can check out/),
    ).toBeInTheDocument();
    expect(accountCalls().map((r) => r.url)).toEqual(["/api/account/register"]);
  });

  it("verifies with the right code", async () => {
    open("/verify-email?reference=reg-1");
    await userEvent.type(screen.getByLabelText("Code"), "123456");
    await userEvent.click(screen.getByRole("button", { name: "Verify" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Your email address is verified");
  });

  it("renders one message for a wrong, an expired and a used code", async () => {
    const messages: (string | null)[] = [];
    for (const [reference, code] of [
      ["reg-1", "000000"], // wrong
      ["reg-expired", "123456"], // expired
      ["reg-used", "123456"], // used
    ]) {
      const { unmount } = open(`/verify-email?reference=${reference}`);
      await userEvent.type(screen.getByLabelText("Code"), code ?? "");
      await userEvent.click(screen.getByRole("button", { name: "Verify" }));
      messages.push((await screen.findByRole("alert")).textContent);
      unmount();
    }

    expect(new Set(messages).size).toBe(1);
    expect(messages[0]).toContain("could not be verified");
  });
});

describe("password reset", () => {
  it("renders the same confirmation for a known and an unknown address", async () => {
    const texts: (string | null)[] = [];
    for (const email of [ADA.email, "nobody@example.test"]) {
      const { unmount } = open("/forgot-password");
      await userEvent.type(screen.getByLabelText("Email"), email);
      await userEvent.click(screen.getByRole("button", { name: "Send reset link" }));
      texts.push((await screen.findByRole("status")).textContent);
      unmount();
    }

    expect(texts[0]).toBe(texts[1]);
    expect(texts[0]).toContain("If that address belongs to an account");
  });

  it("changes the password from the emailed link", async () => {
    const { server } = open("/reset-password?reference=rst-1&code=token-ok");
    await userEvent.type(screen.getByLabelText("New password"), "a-new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Your password was changed");
    expect(server.sent.find((r) => r.url.endsWith("/confirm"))?.body).toEqual({
      reference: "rst-1",
      code: "token-ok",
      newPassword: "a-new-password-1",
    });
  });

  it("gives one message, with a way to ask again, for an unusable link", async () => {
    open("/reset-password?reference=rst-old&code=nope");
    await userEvent.type(screen.getByLabelText("New password"), "a-new-password-1");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    const alert = await screen.findByRole("alert");

    expect(alert).toHaveTextContent("could not be used");
    expect(within(alert).getByRole("link", { name: "Request a new link" })).toHaveAttribute(
      "href",
      "/forgot-password",
    );
  });

  it("shows a refused new password as the server words it, because the customer can fix it", async () => {
    open("/reset-password?reference=rst-1&code=token-ok");
    await userEvent.type(screen.getByLabelText("New password"), "short");
    await userEvent.click(screen.getByRole("button", { name: "Change password" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("at least 8 characters");
  });
});

describe("switching accounts", () => {
  it("replaces the token, refetches, and leaves nothing of the previous account on screen", async () => {
    const { app } = await signInAs(BOB, "/sign-in?from=%2Forders");
    expect(await screen.findByText("100000000777")).toBeInTheDocument();
    // Ada signs in second, so Bob becomes the "other" account to switch back to.
    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
    await userEvent.click(await screen.findByRole("link", { name: "Sign in" }));
    await fillAndSubmit(ADA.email, ADA.password);
    await act(async () => {
      await app.router.navigate("/orders");
    });
    expect(await screen.findByText("100000000001")).toBeInTheDocument();
    expect(screen.queryByText("100000000777")).not.toBeInTheDocument();

    await userEvent.selectOptions(screen.getByLabelText("Switch account"), BOB.email);
    await userEvent.click(screen.getByRole("button", { name: "Switch" }));

    expect(await screen.findByText("100000000777")).toBeInTheDocument();
    expect(screen.queryByText("100000000001")).not.toBeInTheDocument();
    expect(app.tokenStore.get()).toBe("tok-c-bob");
  });

  it("offers nothing to switch to until another account has been signed in to", async () => {
    await signInAs(ADA);

    await screen.findByRole("button", { name: "Sign out" });

    expect(screen.queryByLabelText("Switch account")).not.toBeInTheDocument();
  });
});

describe("signing out", () => {
  const BAR = { id: "p1", name: "Gold Bar 100 g", price: "100.00" };

  it("offers to resume checkout or go home, and the cart is still there", async () => {
    const shop = createShop({ products: [BAR] });
    shop.quantities.set("p1", 2);
    await signInAs(ADA, "/sign-in?from=%2Fcheckout%2Fcs1", { fallback: shop.fetchImpl });
    await screen.findByRole("button", { name: "Sign out" });

    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByRole("heading", { name: "You are signed out" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Resume checkout" })).toHaveAttribute(
      "href",
      "/checkout",
    );
    expect(screen.getByRole("link", { name: "Go to the home page" })).toHaveAttribute("href", "/");
    await waitFor(() => {
      expect(screen.getByTestId("cart-kept")).toHaveTextContent("2 items");
    });
    expect(screen.getByTestId("cart-count")).toHaveTextContent("2");
    // Nothing was done to the cart: it is held by the server, and only read again.
    expect(shop.sent.filter((r) => r.url.startsWith("/api/cart") && r.method !== "GET")).toEqual(
      [],
    );
    expect(shop.quantities.get("p1")).toBe(2);
  });

  it("goes straight home when signing out anywhere else", async () => {
    const { app } = await signInAs(ADA, "/sign-in?from=%2Forders");
    await userEvent.click(await screen.findByRole("button", { name: "Sign out" }));

    await screen.findByRole("heading", { name: "MetalDesk" });

    expect(app.router.state.location.pathname).toBe("/");
    expect(app.tokenStore.get()).toBeNull();
    expect(screen.queryByRole("heading", { name: "You are signed out" })).not.toBeInTheDocument();
  });
});

describe("order history", () => {
  it("shows the total the server sent, not a sum of the lines", async () => {
    await signInAs(BOB, "/sign-in?from=%2Forders%2F100000000777");

    expect(await screen.findByTestId("grand-total")).toHaveTextContent("999.00");
    // The lines add up to 250.00 net; that is not what the order cost and appears nowhere as a total.
    expect(screen.queryByText("€250.00", { selector: "dd" })).not.toBeInTheDocument();
  });

  it("shows the list total from the server too", async () => {
    await signInAs(BOB, "/sign-in?from=%2Forders");

    expect(await screen.findByTestId("order-total")).toHaveTextContent("999.00");
  });

  it("shows carrier and tracking for a shipped order", async () => {
    await signInAs(BOB, "/sign-in?from=%2Forders%2F100000000777");

    const shipment = within(await screen.findByTestId("shipment"));

    expect(shipment.getByText("DHL")).toBeInTheDocument();
    expect(shipment.getByText("TRACK-42")).toBeInTheDocument();
  });

  it("renders no shipment block at all before shipping", async () => {
    await signInAs(ADA, "/sign-in?from=%2Forders%2F100000000001");

    await screen.findByTestId("grand-total");

    expect(screen.queryByTestId("shipment")).not.toBeInTheDocument();
    expect(screen.queryByText("Carrier")).not.toBeInTheDocument();
    expect(screen.queryByText("Tracking number")).not.toBeInTheDocument();
  });

  it("shows an explicit empty state for a customer with no orders", async () => {
    const empty = { ...ADA, orders: [] };
    await signInAs(empty, "/sign-in?from=%2Forders", { accounts: [empty] });

    expect(await screen.findByRole("heading", { name: "No orders yet" })).toBeInTheDocument();
  });

  it("never shows another customer's order", async () => {
    await signInAs(ADA, "/sign-in?from=%2Forders%2F100000000777");

    expect(await screen.findByRole("alert")).toHaveTextContent("No such order");
  });
});

describe("the eight order statuses", () => {
  it.each(orderStatusSchema.options)("renders a label for %s", (status) => {
    renderWithPreferences(<OrderStatusBadge status={status} statusLabel="" />);

    expect(screen.getByText(/\S/)).toBeInTheDocument();
    expect(document.body.textContent?.trim()).not.toBe("");
    expect(document.body.textContent).not.toContain("order.status.");
  });

  it("includes the manager handoff's status in words", () => {
    renderWithPreferences(<OrderStatusBadge status="AWAITING_MANAGER_QUOTE" statusLabel="" />);

    expect(screen.getByText("Awaiting your manager's quote")).toBeInTheDocument();
  });

  it("falls back to the server's label, then the name, for a status it does not know yet", () => {
    const { unmount } = renderWithPreferences(
      <OrderStatusBadge status="RETURNED" statusLabel="Returned to us" />,
    );
    expect(screen.getByText("Returned to us")).toBeInTheDocument();
    unmount();

    renderWithPreferences(<OrderStatusBadge status="RETURNED" statusLabel="" />);
    expect(screen.getByText("RETURNED")).toBeInTheDocument();
  });

  it("lists orders in every status with a label", async () => {
    const orders = orderStatusSchema.options.map((status, index) => ({
      number: `10000000010${String(index)}`,
      status,
      total: "10.00",
    }));
    const account = { ...ADA, orders };
    await signInAs(account, "/sign-in?from=%2Forders", { accounts: [account] });

    const rows = await screen.findAllByTestId("order-row");

    expect(rows).toHaveLength(8);
    for (const row of rows) {
      expect(row.querySelector(".md-status")?.textContent).toBeTruthy();
    }
  });
});

describe("inquiries", () => {
  async function send(path: string) {
    const opened = open(path);
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.type(screen.getByLabelText("Email"), "ada@example.test");
    await userEvent.type(screen.getByLabelText("Subject"), "A question");
    await userEvent.type(screen.getByLabelText("Message"), "Hello there");
    await userEvent.click(screen.getByRole("button", { name: "Send message" }));
    return opened;
  }

  it.each([
    ["/inquiry", { source: "CATALOG" }],
    ["/inquiry?productId=p1", { source: "PRODUCT", productId: "p1" }],
    [
      "/inquiry?handoffReference=100000000042",
      { source: "HANDOFF", handoffReference: "100000000042" },
    ],
  ])("submits an anonymous inquiry from %s and shows the reference", async (path, expected) => {
    const { server, app } = await send(path);

    expect(await screen.findByTestId("inquiry-reference")).toHaveTextContent("INQ-ABC123");
    const request = server.sent.find((r) => r.url === "/api/inquiries");
    expect(request?.body).toMatchObject({ name: "Ada", email: "ada@example.test", ...expected });
    expect(request?.headers.get("authorization")).toBeNull();
    expect(app.tokenStore.get()).toBeNull();
  });

  it("does not send a product id with a handoff inquiry or the reverse", async () => {
    const { server } = await send("/inquiry?handoffReference=100000000042&productId=p1");

    await screen.findByTestId("inquiry-reference");

    expect(server.sent.find((r) => r.url === "/api/inquiries")?.body).not.toHaveProperty(
      "productId",
    );
  });

  it("shows each violation beside its own field", async () => {
    open("/inquiry");
    await userEvent.type(screen.getByLabelText("Name"), "Ada");
    await userEvent.click(screen.getByRole("button", { name: "Send message" }));

    await waitFor(() => {
      expect(screen.getByLabelText("Email")).toHaveAccessibleDescription("email is required");
    });
    expect(screen.getByLabelText("Message")).toHaveAccessibleDescription("message is required");
    expect(screen.getByLabelText("Name")).not.toHaveAccessibleDescription();
  });
});
