import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import App from "../App";
import { createApp } from "../app/createApp";
import { createAdminServer } from "../test/adminServer";

function open(path: string, state: Parameters<typeof createAdminServer>[0] = {}) {
  const server = createAdminServer(state);
  const app = createApp({ initialEntries: [path], baseUrl: "/api", fetchImpl: server.fetchImpl });
  render(<App app={app} />);
  return { server, app };
}

async function signInAs(login: string, password: string) {
  await userEvent.type(screen.getByLabelText("Login"), login);
  await userEvent.type(screen.getByLabelText("Password"), password);
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
}

describe("the login form", () => {
  it("sends an unauthenticated visitor to it, remembering where they were going", async () => {
    const { app, server } = open("/orders");

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(app.router.state.location.pathname).toBe("/login");
    expect(app.router.state.location.search).toBe("?from=%2Forders");
    // Nothing of the protected page was requested without a token.
    expect(server.sent.filter((request) => request.url.startsWith("/api/admin/orders"))).toEqual(
      [],
    );
  });

  it("signs in with a login and password, then lands on the page that was asked for", async () => {
    const { app, server } = open("/login?from=%2Forders");

    await signInAs("manager", "manager");

    expect(await screen.findByRole("heading", { name: "Orders" })).toBeInTheDocument();
    expect(app.router.state.location.pathname).toBe("/orders");
    expect(screen.getByTestId("staff-identity")).toHaveTextContent(
      "Signed in as manager (MANAGER)",
    );
    expect(app.tokenStore.get()).toBe("token-for-manager");
    const signIn = server.sent.find((request) => request.url === "/api/admin/auth/sign-in");
    expect(signIn?.body).toEqual({ login: "manager", password: "manager" });
  });

  it("shows the admin's role", async () => {
    open("/login");

    await signInAs("admin", "admin");

    await waitFor(() => {
      expect(screen.getByTestId("staff-identity")).toHaveTextContent("Signed in as admin (ADMIN)");
    });
  });

  it("says the same thing whatever was wrong, and stores no token", async () => {
    const { app } = open("/login");

    await signInAs("admin", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid login or password.");
    expect(app.tokenStore.get()).toBeNull();
    expect(app.router.state.location.pathname).toBe("/login");
  });

  it("tells a locked-out user to wait", async () => {
    open("/login", { throttled: true });

    await signInAs("admin", "admin");

    expect(await screen.findByRole("alert")).toHaveTextContent("Too many attempts");
  });

  it("does not follow a `from` that leaves the app", async () => {
    const { app } = open("/login?from=%2F%2Fevil.example");

    await signInAs("admin", "admin");

    await screen.findByTestId("staff-identity");
    expect(app.router.state.location.pathname).toBe("/");
  });
});

describe("signing out and expiry", () => {
  it("signing out discards the token and returns to the form", async () => {
    const { app } = open("/login");
    await signInAs("manager", "manager");
    await screen.findByTestId("staff-identity");

    await userEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(app.tokenStore.get()).toBeNull();
  });

  it("a token the server stops accepting sends the user back to the form", async () => {
    const { server, app } = open("/login");
    await signInAs("manager", "manager");
    await screen.findByRole("heading", { name: "MetalDesk Admin" });

    server.expireTokens();
    await app.router.navigate("/tiers");

    expect(await screen.findByRole("heading", { name: "Sign in" })).toBeInTheDocument();
    expect(app.tokenStore.get()).toBeNull();
  });
});
