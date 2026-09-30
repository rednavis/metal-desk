import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import App from "../App";
import { createApp } from "../app/createApp";
import { ApiError } from "../api/errors";
import { ErrorState } from "../ui";

function renderAt(path: string) {
  render(<App app={createApp({ initialEntries: [path], baseUrl: "/api" })} />);
}

describe("the back office's routes", () => {
  it("renders the overview", () => {
    renderAt("/");

    expect(screen.getByRole("heading", { name: "MetalDesk Admin" })).toBeInTheDocument();
  });

  it("renders the tiers page", () => {
    renderAt("/tiers");

    expect(screen.getByRole("heading", { name: "Delivery tiers" })).toBeInTheDocument();
  });

  it("has no sign-in page: the identity-aware proxy owns authentication", () => {
    renderAt("/sign-in");

    expect(screen.getByRole("heading", { name: "Page not found" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /sign in/i })).not.toBeInTheDocument();
  });
});

describe("the error state", () => {
  it("shows the correlation id so it can be quoted to support", () => {
    render(<ErrorState error={new ApiError(409, "order.changed", "Order changed", "ref-42")} />);

    expect(screen.getByRole("alert")).toHaveTextContent("Order changed");
    expect(screen.getByText("ref-42")).toBeInTheDocument();
  });
});
