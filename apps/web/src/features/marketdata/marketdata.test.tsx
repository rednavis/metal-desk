import { act, fireEvent, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { MarketDataConfig } from "../../config";
import { memoryStorage, renderWithPreferences } from "../../test/render";
import { MarketDataPanel } from "./MarketDataPanel";
import { backoffDelay } from "./useReferencePrices";

/** Timers fire at their time; the render that schedules the next one lands a few milliseconds later. */
const SLACK = 50;

const CONFIG: MarketDataConfig = {
  pollIntervalMs: 10_000,
  staleAfterMs: 35_000,
  retryBaseMs: 1_000,
  retryMaxMs: 4_000,
};

/** The instant the tests start at; prices are observed "now" unless a test says otherwise. */
const STARTED = "2026-10-01T10:00:00.000Z";

interface Row {
  metal: string;
  pricePerGram: string;
  currency?: string;
  change?: { direction: string; amount: string; percent: string };
  observedAt?: string;
}

function row(overrides: Row) {
  return { currency: "EUR", observedAt: STARTED, ...overrides };
}

const GOLD_UP = row({
  metal: "GOLD",
  pricePerGram: "63.00",
  change: { direction: "UP", amount: "3.00", percent: "5.00" },
});
const SILVER_DOWN = row({
  metal: "SILVER",
  pricePerGram: "0.72",
  change: { direction: "DOWN", amount: "0.08", percent: "10.00" },
});
const PLATINUM_FLAT = row({
  metal: "PLATINUM",
  pricePerGram: "30.00",
  change: { direction: "UNCHANGED", amount: "0.00", percent: "0.00" },
});
const PALLADIUM_NEW = row({ metal: "PALLADIUM", pricePerGram: "32.00" });

/**
 * A response that settles on microtasks alone. A real `Response` reads its body on the platform's own
 * schedule, which fake timers cannot advance, and the test would race the clock it is trying to control.
 */
function reply(status: number, body: unknown): Response {
  return {
    status,
    ok: status < 400,
    text: () => Promise.resolve(JSON.stringify(body)),
  } as Response;
}

/** A network whose answers a test scripts: each call takes the next step, the last one repeats. */
function network(...steps: (Row[] | "fail")[]) {
  let calls = 0;
  const requests: { url: string; headers: Headers }[] = [];
  const fetchImpl: typeof globalThis.fetch = (input, init) => {
    const target =
      typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    requests.push({ url: target, headers: new Headers(init?.headers) });
    const step = steps[Math.min(calls, steps.length - 1)];
    calls += 1;
    return step === "fail"
      ? Promise.resolve(reply(500, { code: "internal-error", message: "down", correlationId: "c" }))
      : Promise.resolve(reply(200, step));
  };
  return { fetchImpl, calls: () => calls, requests };
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

function setVisible(visible: boolean) {
  Object.defineProperty(document, "hidden", { configurable: true, get: () => !visible });
  act(() => {
    document.dispatchEvent(new Event("visibilitychange"));
  });
}

async function mount(
  fetchImpl: typeof globalThis.fetch,
  config = CONFIG,
  storage = memoryStorage(),
) {
  renderWithPreferences(<MarketDataPanel config={config} />, { fetchImpl, storage });
  await advance(0);
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: false });
  vi.setSystemTime(new Date("2026-10-01T10:00:00Z"));
  setVisible(true);
});

afterEach(() => {
  vi.useRealTimers();
  Object.defineProperty(document, "hidden", { configurable: true, get: () => false });
});

describe("the reference-price panel", () => {
  it("renders one row per metal with a formatted price per gram", async () => {
    await mount(network([GOLD_UP, SILVER_DOWN, PLATINUM_FLAT, PALLADIUM_NEW]).fetchImpl);

    const rows = screen.getAllByRole("row").slice(1);
    expect(rows).toHaveLength(4);
    expect(within(rows[0]).getByRole("rowheader")).toHaveTextContent("Gold");
    expect(rows[0]).toHaveTextContent("€63.00");
    expect(rows[1]).toHaveTextContent("€0.72");
  });

  it("shows prices in the active currency, asking the server to convert", async () => {
    const storage = memoryStorage();
    storage.setItem("metaldesk.preferences", JSON.stringify({ currency: "USD" }));
    const net = network([row({ metal: "GOLD", pricePerGram: "126.00", currency: "USD" })]);

    await mount(net.fetchImpl, CONFIG, storage);

    expect(screen.getByRole("row", { name: /Gold/ })).toHaveTextContent("$126.00");
    expect(net.requests[0]?.url).toBe("/api/market-data/prices?currency=USD");
  });

  it("needs no sign-in and sends no credential", async () => {
    const net = network([GOLD_UP]);

    await mount(net.fetchImpl);

    expect(screen.getByRole("row", { name: /Gold/ })).toBeInTheDocument();
    expect(net.requests.every((request) => !request.headers.has("authorization"))).toBe(true);
  });

  it("says when there are no prices yet", async () => {
    await mount(network([]).fetchImpl);

    expect(screen.getByRole("heading", { name: "No prices yet" })).toBeInTheDocument();
  });
});

describe("the change of a price", () => {
  it("shows a rise as up, with its size", async () => {
    await mount(network([GOLD_UP]).fetchImpl);

    const gold = screen.getByRole("row", { name: /Gold/ });
    expect(gold).toHaveAttribute("data-direction", "UP");
    expect(gold).toHaveTextContent("▲");
    expect(gold).toHaveTextContent("€3.00 (5.00%)");
    expect(within(gold).getByText("Up €3.00 (5.00%)")).toBeInTheDocument();
  });

  it("shows a fall as down, with its size", async () => {
    await mount(network([SILVER_DOWN]).fetchImpl);

    const silver = screen.getByRole("row", { name: /Silver/ });
    expect(silver).toHaveAttribute("data-direction", "DOWN");
    expect(silver).toHaveTextContent("▼");
    expect(within(silver).getByText("Down €0.08 (10.00%)")).toBeInTheDocument();
  });

  it("shows an unchanged price as unchanged", async () => {
    await mount(network([PLATINUM_FLAT]).fetchImpl);

    const platinum = screen.getByRole("row", { name: /Platinum/ });
    expect(platinum).toHaveAttribute("data-direction", "UNCHANGED");
    expect(within(platinum).getByText("Unchanged")).toBeInTheDocument();
  });

  it("shows an absent change as unknown: no arrow, no colour class, no zero", async () => {
    await mount(network([PALLADIUM_NEW]).fetchImpl);

    const palladium = screen.getByRole("row", { name: /Palladium/ });
    expect(palladium).toHaveAttribute("data-direction", "UNKNOWN");
    expect(palladium).not.toHaveTextContent(/[▲▼▬]/);
    expect(palladium).not.toHaveTextContent("0.00");
    expect(palladium).toHaveTextContent("Change not yet available");
    const cell = palladium.querySelector("td.md-change");
    expect(cell).toHaveClass("md-change--unknown");
    expect(cell).not.toHaveClass("md-change--up", "md-change--down");
  });

  it("never relies on colour: the glyph is hidden from screen readers and a sentence is read instead", async () => {
    await mount(network([GOLD_UP]).fetchImpl);

    const cell = screen.getByRole("row", { name: /Gold/ }).querySelector("td.md-change")!;
    expect(cell.querySelector("[aria-hidden='true']")).toHaveTextContent("▲");
    expect(cell.querySelector(".md-sr-only")).toHaveTextContent("Up €3.00 (5.00%)");
  });
});

describe("polling", () => {
  it("refreshes on the configured interval", async () => {
    const net = network([GOLD_UP]);
    await mount(net.fetchImpl);
    expect(net.calls()).toBe(1);

    await advance(CONFIG.pollIntervalMs);
    expect(net.calls()).toBe(2);
    await advance(CONFIG.pollIntervalMs);
    expect(net.calls()).toBe(3);
  });

  it("stops while the tab is hidden and refetches at once when it is visible again", async () => {
    const net = network([GOLD_UP]);
    await mount(net.fetchImpl);

    setVisible(false);
    await advance(CONFIG.pollIntervalMs * 5);
    expect(net.calls()).toBe(1);

    setVisible(true);
    await advance(0);
    expect(net.calls()).toBe(2);
  });
});

describe("a failed refresh", () => {
  it("keeps the last values, shows an error with a retry, and backs off", async () => {
    const net = network([GOLD_UP], "fail");
    await mount(net.fetchImpl);

    await advance(CONFIG.pollIntervalMs);
    expect(net.calls()).toBe(2);
    expect(screen.getByRole("row", { name: /Gold/ })).toHaveTextContent("€63.00");
    expect(screen.getByRole("alert")).toHaveTextContent("Prices could not be refreshed");

    // Each retry comes after its delay and not before: 1 s, 2 s, 4 s, then the 4 s cap. Never the 10 s
    // polling interval.
    let expected = 2;
    for (const delay of [1_000, 2_000, 4_000, 4_000]) {
      await advance(delay - SLACK * 3);
      expect(net.calls(), `before ${delay} ms`).toBe(expected);
      await advance(SLACK * 4);
      expected += 1;
      expect(net.calls(), `after ${delay} ms`).toBe(expected);
    }
  });

  it("returns to the normal interval once a refresh succeeds", async () => {
    const net = network([GOLD_UP], "fail", [GOLD_UP]);
    await mount(net.fetchImpl);
    await advance(CONFIG.pollIntervalMs);
    await advance(CONFIG.retryBaseMs + SLACK);
    expect(net.calls()).toBe(3);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    await advance(CONFIG.pollIntervalMs - SLACK * 2);
    expect(net.calls()).toBe(3);
    await advance(SLACK * 3);
    expect(net.calls()).toBe(4);
  });

  it("lets the customer retry now", async () => {
    const net = network([GOLD_UP], "fail");
    await mount(net.fetchImpl);
    await advance(CONFIG.pollIntervalMs);
    const before = net.calls();

    fireEvent.click(screen.getByRole("button", { name: "Retry now" }));
    await advance(0);

    expect(net.calls()).toBe(before + 1);
  });

  it("with nothing to show yet, shows the error and a retry instead of a blank", async () => {
    const net = network("fail", [GOLD_UP]);
    await mount(net.fetchImpl);

    expect(screen.getByRole("alert")).toHaveTextContent("Something went wrong");

    fireEvent.click(screen.getByRole("button", { name: "Retry now" }));
    await advance(0);
    expect(screen.getByRole("row", { name: /Gold/ })).toBeInTheDocument();
  });
});

describe("stale prices", () => {
  it("are flagged once the last successful fetch is older than the threshold", async () => {
    const net = network([GOLD_UP], "fail");
    await mount(net.fetchImpl);
    expect(screen.queryByRole("status", { name: "" })).toBeNull();
    expect(screen.queryByText(/may be out of date/)).not.toBeInTheDocument();

    await advance(CONFIG.staleAfterMs + CONFIG.pollIntervalMs);

    expect(screen.getByText(/may be out of date: the last successful update/)).toBeInTheDocument();
    expect(screen.getByRole("table")).toHaveAttribute("data-stale", "true");
  });

  it("are flagged when fetches succeed but the feed behind them has gone quiet", async () => {
    const old = new Date(Date.now() - CONFIG.staleAfterMs * 2).toISOString();
    await mount(
      network([row({ metal: "GOLD", pricePerGram: "63.00", observedAt: old })]).fetchImpl,
    );

    expect(screen.getByText(/price feed has not updated since/)).toBeInTheDocument();
    expect(screen.getByRole("table")).toHaveAttribute("data-stale", "true");
  });

  it("are not flagged while fresh", async () => {
    await mount(network([GOLD_UP]).fetchImpl);

    expect(screen.getByRole("table")).toHaveAttribute("data-stale", "false");
  });
});

describe("the backoff", () => {
  it("doubles from its base and stops at its cap", () => {
    expect([1, 2, 3, 4, 5].map((n) => backoffDelay(n, 1_000, 4_000))).toEqual([
      1_000, 2_000, 4_000, 4_000, 4_000,
    ]);
  });
});
