/**
 * Where the customer's bearer token lives: **in memory**, in this module's closure, and nowhere else.
 *
 * The trade-off, decided here so nobody has to rediscover it:
 *
 * - `localStorage` survives a reload and a new tab, but any script on the page can read it, so one
 *   XSS flaw would hand over every customer's session.
 * - `sessionStorage` narrows that to one tab but is just as readable by an injected script.
 * - Memory is out of reach of everything but code that already runs in this app, and is lost on a
 *   reload, which signs the customer out.
 *
 * Memory wins because the cost of losing the session is small: the API issues a 15-minute token
 * with no refresh (T-032), so there is little to preserve across a reload, and the cart belongs to
 * a cookie, not to the token, so it survives sign-out and reload anyway (FR-2.7). If tokens ever
 * become long-lived, revisit this together with a refresh flow, not by moving the token to
 * `localStorage` alone.
 */
export interface TokenStore {
  get: () => string | null;
  set: (token: string) => void;
  clear: () => void;
  /** Calls `listener` whenever the token is set or cleared; returns the way to stop. */
  subscribe: (listener: () => void) => () => void;
}

export function createMemoryTokenStore(): TokenStore {
  let token: string | null = null;
  const listeners = new Set<() => void>();
  const notify = () => {
    listeners.forEach((listener) => {
      listener();
    });
  };
  return {
    get: () => token,
    set: (next) => {
      token = next;
      notify();
    },
    clear: () => {
      if (token !== null) {
        token = null;
        notify();
      }
    },
    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
  };
}
