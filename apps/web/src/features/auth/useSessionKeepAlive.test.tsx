import { act, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ApiClient } from "../../api/client";
import { createMemoryTokenStore } from "../../api/tokenStore";
import { REFRESH_INTERVAL_MS, useSessionKeepAlive } from "./useSessionKeepAlive";

const REFRESHED = { accessToken: "fresh", tokenType: "Bearer", expiresInSeconds: 1800 };

function setup(signedIn: boolean) {
  const store = createMemoryTokenStore();
  if (signedIn) store.set("old");
  const post = vi.fn(() => Promise.resolve(REFRESHED));
  const client = { post } as unknown as ApiClient;
  function Probe({ on }: { on: boolean }) {
    useSessionKeepAlive(client, store, on);
    return null;
  }
  const view = render(<Probe on={signedIn} />);
  return { store, post, view, Probe };
}

async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

describe("keeping a session alive", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-01T10:00:00Z"));
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("swaps a saved token at once when the page opens signed in", async () => {
    const { store, post } = setup(true);
    await flush();

    expect(post).toHaveBeenCalledWith("/auth/refresh", expect.anything());
    expect(store.get()).toBe("fresh");
  });

  it("does nothing for a signed-out visitor", async () => {
    const { post } = setup(false);

    act(() => {
      document.dispatchEvent(new Event("pointerdown"));
    });
    await flush();

    expect(post).not.toHaveBeenCalled();
  });

  it("swaps at most once a minute, and again on activity after that", async () => {
    const { post } = setup(true);
    await flush();
    expect(post).toHaveBeenCalledTimes(1);

    vi.setSystemTime(Date.now() + REFRESH_INTERVAL_MS - 1000);
    act(() => {
      document.dispatchEvent(new Event("keydown"));
    });
    await flush();
    expect(post).toHaveBeenCalledTimes(1);

    vi.setSystemTime(Date.now() + 2000);
    act(() => {
      document.dispatchEvent(new Event("pointerdown"));
    });
    await flush();
    expect(post).toHaveBeenCalledTimes(2);
  });

  it("does not swap on its own: no activity, no refresh", async () => {
    const { post } = setup(true);
    await flush();

    vi.setSystemTime(Date.now() + 10 * REFRESH_INTERVAL_MS);
    await flush();

    expect(post).toHaveBeenCalledTimes(1);
  });

  it("does not swap straight after a sign-in on this page, which just issued a token", async () => {
    const { store, post, view, Probe } = setup(false);

    store.set("just-signed-in");
    view.rerender(<Probe on={true} />);
    await flush();

    expect(post).not.toHaveBeenCalled();
  });

  it("survives a failed swap and tries again on the next action", async () => {
    const store = createMemoryTokenStore();
    store.set("old");
    const post = vi.fn().mockRejectedValueOnce(new Error("offline")).mockResolvedValue(REFRESHED);
    const client = { post } as unknown as ApiClient;
    function Probe() {
      useSessionKeepAlive(client, store, true);
      return null;
    }
    render(<Probe />);
    await flush();
    expect(store.get()).toBe("old");

    vi.setSystemTime(Date.now() + REFRESH_INTERVAL_MS + 1);
    act(() => {
      document.dispatchEvent(new Event("pointerdown"));
    });
    await flush();

    expect(store.get()).toBe("fresh");
  });
});
