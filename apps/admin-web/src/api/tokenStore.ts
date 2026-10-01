/**
 * Where the signed-in user's bearer token lives: **in memory**, in this module's closure, and
 * nowhere else.
 *
 * Browser storage survives a reload but is readable by any script on the page, so one XSS flaw would
 * hand over a back-office session; memory is out of reach of everything but code already running in
 * this app. The cost is that a reload signs the user out, which is cheap: the server issues a
 * short-lived token with no refresh, so there is little to preserve. If tokens ever become
 * long-lived, revisit this together with a refresh flow, not by moving the token to `localStorage`
 * alone.
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
