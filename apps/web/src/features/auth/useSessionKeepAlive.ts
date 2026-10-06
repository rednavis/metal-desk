import { useEffect, useRef } from "react";
import type { ApiClient } from "../../api/client";
import { signInResponseSchema } from "../../api/types";
import type { TokenStore } from "../../api/tokenStore";

/** How long a session lasts without activity; it is the lifetime the server gives every token. */
export const IDLE_TIMEOUT_MINUTES = 30;

/** At most this often is the token swapped, however busy the user is. */
export const REFRESH_INTERVAL_MS = 60_000;

const ACTIVITY_EVENTS = ["pointerdown", "keydown", "touchstart", "wheel"] as const;

/**
 * Keeps a signed-in session alive while it is used and lets it end when it is not.
 *
 * The server gives every token the idle timeout ({@link IDLE_TIMEOUT_MINUTES} minutes) as its
 * lifetime. While the user clicks, types, scrolls or comes back to the tab, the token is swapped for
 * a new one with a full lifetime, at most once a minute, so the session ends {@link IDLE_TIMEOUT_MINUTES}
 * minutes after the last swap, which is at most a minute after the last action. A page that is
 * opened with a saved token swaps it at once, which also finds out whether it is still valid. A
 * refused swap (401) is handled by the API client like any other 401; a network failure is ignored
 * and tried again on the next action.
 */
export function useSessionKeepAlive(
  client: ApiClient,
  tokenStore: TokenStore | null,
  signedIn: boolean,
): void {
  // A page that starts with a token refreshes it at once; a sign-in on this page just got a fresh one.
  const startedSignedIn = useRef(signedIn);

  useEffect(() => {
    if (!signedIn || tokenStore === null) return undefined;
    let lastRefresh = startedSignedIn.current ? 0 : Date.now();
    startedSignedIn.current = false;
    let inFlight = false;

    const touch = () => {
      const now = Date.now();
      if (inFlight || now - lastRefresh < REFRESH_INTERVAL_MS) return;
      lastRefresh = now;
      inFlight = true;
      client
        .post("/auth/refresh", { schema: signInResponseSchema })
        .then((response) => {
          tokenStore.set(response.accessToken);
        })
        .catch(() => {
          // A 401 was already handled by the client; anything else is tried again on the next action.
        })
        .finally(() => {
          inFlight = false;
        });
    };
    const onVisible = () => {
      if (document.visibilityState === "visible") touch();
    };

    touch();
    for (const name of ACTIVITY_EVENTS) document.addEventListener(name, touch, { passive: true });
    document.addEventListener("visibilitychange", onVisible);
    return () => {
      for (const name of ACTIVITY_EVENTS) document.removeEventListener(name, touch);
      document.removeEventListener("visibilitychange", onVisible);
    };
  }, [client, tokenStore, signedIn]);
}
