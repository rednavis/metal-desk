import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { useCart } from "../features/cart/useCart";
import { createFakeServer } from "../test/fakeServer";
import { memoryStorage, renderWithPreferences, stubDeviceTheme } from "../test/render";
import { TotalsSummary } from "../ui";
import { usePreferences } from "./usePreferences";

function Probe() {
  const { theme, locale, currency, setTheme, setLocale, setCurrency, t } = usePreferences();
  return (
    <>
      <p data-testid="title">{t("page.cart.title")}</p>
      <p data-testid="state">{`${theme}|${locale}|${currency}`}</p>
      <button
        onClick={() => {
          setTheme("dark");
        }}
      >
        dark
      </button>
      <button
        onClick={() => {
          setTheme("light");
        }}
      >
        light
      </button>
      <button
        onClick={() => {
          setLocale("de");
        }}
      >
        german
      </button>
      <button
        onClick={() => {
          setCurrency("USD");
        }}
      >
        dollars
      </button>
    </>
  );
}

function CartTotals() {
  const cart = useCart();
  return cart.data?.totals ? <TotalsSummary totals={cart.data.totals} /> : <p>loading</p>;
}

const theme = () => document.documentElement.dataset["theme"];
const state = () => screen.getByTestId("state").textContent;

beforeEach(() => {
  delete document.documentElement.dataset["theme"];
  stubDeviceTheme(false);
});
afterEach(() => {
  delete document.documentElement.dataset["theme"];
});

describe("switching without a reload", () => {
  it("switches the theme at the root", async () => {
    renderWithPreferences(<Probe />);
    expect(theme()).toBe("light");

    await userEvent.click(screen.getByRole("button", { name: "dark" }));

    expect(theme()).toBe("dark");
  });

  it("re-renders translated content when the language changes", async () => {
    renderWithPreferences(<Probe />);
    expect(screen.getByTestId("title")).toHaveTextContent("Cart");

    await userEvent.click(screen.getByRole("button", { name: "german" }));

    expect(screen.getByTestId("title")).toHaveTextContent("Warenkorb");
    expect(document.documentElement.lang).toBe("de");
  });

  it("recomputes prices, taxes and totals when the currency changes", async () => {
    const server = createFakeServer();
    renderWithPreferences(
      <>
        <Probe />
        <CartTotals />
      </>,
      { fetchImpl: server.fetchImpl },
    );
    expect(await screen.findByTestId("total")).toHaveTextContent("€119.00");
    expect(screen.getByTestId("net")).toHaveTextContent("€100.00");
    expect(screen.getByTestId("tax")).toHaveTextContent("€19.00");

    await userEvent.click(screen.getByRole("button", { name: "dollars" }));

    await waitFor(() => {
      expect(screen.getByTestId("total")).toHaveTextContent("$238.00");
    });
    expect(screen.getByTestId("net")).toHaveTextContent("$200.00");
    expect(screen.getByTestId("tax")).toHaveTextContent("$38.00");
    const last = server.sent.filter((request) => request.url.startsWith("/api/cart")).at(-1);
    expect(last?.url).toBe("/api/cart?currency=USD");
  });

  it("formats the same totals for the active language", async () => {
    renderWithPreferences(
      <>
        <Probe />
        <CartTotals />
      </>,
      { fetchImpl: createFakeServer().fetchImpl },
    );
    await screen.findByTestId("total");

    await userEvent.click(screen.getByRole("button", { name: "german" }));

    await waitFor(() => {
      expect(screen.getByTestId("total").textContent).toMatch(/119,00\s€/);
    });
  });
});

describe("the initial theme", () => {
  it("follows a dark device when nothing is stored", () => {
    stubDeviceTheme(true);

    renderWithPreferences(<Probe />);

    expect(theme()).toBe("dark");
    expect(state()).toBe("system|en|EUR");
  });

  it("follows a light device when nothing is stored", () => {
    renderWithPreferences(<Probe />);

    expect(theme()).toBe("light");
  });

  it("yields to a stored choice", () => {
    stubDeviceTheme(true);
    const storage = memoryStorage();
    storage.setItem("metaldesk.preferences", JSON.stringify({ theme: "light" }));

    renderWithPreferences(<Probe />, { storage });

    expect(theme()).toBe("light");
  });
});

describe("an anonymous visitor's choices", () => {
  it("survive a reload", async () => {
    const storage = memoryStorage();
    const first = renderWithPreferences(<Probe />, { storage });
    await userEvent.click(screen.getByRole("button", { name: "dark" }));
    await userEvent.click(screen.getByRole("button", { name: "german" }));
    await userEvent.click(screen.getByRole("button", { name: "dollars" }));
    first.unmount();
    delete document.documentElement.dataset["theme"];

    renderWithPreferences(<Probe />, { storage });

    expect(state()).toBe("dark|de|USD");
    expect(theme()).toBe("dark");
  });

  it("are never sent to the server", async () => {
    const server = createFakeServer();
    renderWithPreferences(<Probe />, { fetchImpl: server.fetchImpl });

    await userEvent.click(screen.getByRole("button", { name: "dark" }));

    expect(server.sent.filter((request) => request.url.includes("/account/preferences"))).toEqual(
      [],
    );
  });
});

describe("a signed-in customer's choices", () => {
  it("persist across a new session, through the server", async () => {
    const server = createFakeServer();
    const firstSession = renderWithPreferences(<Probe />, { fetchImpl: server.fetchImpl });
    act(() => {
      firstSession.tokenStore.set("jwt");
    });
    await waitFor(() => {
      expect(server.sent.some((r) => r.method === "GET")).toBe(true);
    });
    await userEvent.click(screen.getByRole("button", { name: "dark" }));
    await waitFor(() => {
      expect(server.stored().theme).toBe("DARK");
    });
    firstSession.unmount();
    delete document.documentElement.dataset["theme"];

    // A new session: nothing stored in this browser, same account.
    const secondSession = renderWithPreferences(<Probe />, {
      fetchImpl: server.fetchImpl,
      storage: memoryStorage(),
    });
    expect(theme()).toBe("light");
    act(() => {
      secondSession.tokenStore.set("jwt");
    });

    await waitFor(() => {
      expect(theme()).toBe("dark");
    });
    expect(state()).toContain("dark");
  });

  it("on sign-in, the server wins where it has a value and the local choice fills the rest", async () => {
    const server = createFakeServer({ theme: "DARK" });
    const storage = memoryStorage();
    storage.setItem("metaldesk.preferences", JSON.stringify({ theme: "light", locale: "de" }));
    const { tokenStore } = renderWithPreferences(<Probe />, {
      fetchImpl: server.fetchImpl,
      storage,
    });
    expect(state()).toBe("light|de|EUR");

    act(() => {
      tokenStore.set("jwt");
    });

    await waitFor(() => {
      expect(state()).toBe("dark|de|EUR");
    });
    // The local language the server lacked is pushed up; the server's theme is not overwritten.
    await waitFor(() => {
      expect(server.stored()).toMatchObject({ theme: "DARK", locale: "de" });
    });
  });

  it("sends each change to the server while signed in", async () => {
    const server = createFakeServer({ theme: "DARK", locale: "en", currency: "EUR" });
    const { tokenStore } = renderWithPreferences(<Probe />, { fetchImpl: server.fetchImpl });
    act(() => {
      tokenStore.set("jwt");
    });
    await waitFor(() => {
      expect(state()).toBe("dark|en|EUR");
    });

    await userEvent.click(screen.getByRole("button", { name: "light" }));

    await waitFor(() => {
      expect(server.stored().theme).toBe("LIGHT");
    });
  });
});
