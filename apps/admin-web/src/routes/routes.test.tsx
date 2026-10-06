import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "../App";
import { createApp } from "../app/createApp";
import { ApiError } from "../api/errors";
import { createAdminServer } from "../test/adminServer";
import { ErrorState } from "../ui";

function renderAt(path: string) {
  const app = createApp({
    initialEntries: [path],
    baseUrl: "/api",
    fetchImpl: createAdminServer().fetchImpl,
  });
  app.tokenStore.set("token-for-manager");
  render(<App app={app} />);
}

describe("the back office's routes", () => {
  it("renders the overview", async () => {
    renderAt("/");

    expect(await screen.findByRole("heading", { name: "MetalDesk Admin" })).toBeInTheDocument();
  });

  it("renders the tiers page", async () => {
    renderAt("/tiers");

    expect(await screen.findByRole("heading", { name: "Delivery tiers" })).toBeInTheDocument();
  });
});

describe("the error state", () => {
  it("shows the correlation id so it can be quoted to support", () => {
    render(<ErrorState error={new ApiError(409, "order.changed", "Order changed", "ref-42")} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Order changed");
    expect(screen.getByText("ref-42")).toBeInTheDocument();
  });
});
