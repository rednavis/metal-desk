/**
 * Where the customer's bearer token lives. In the running app it is **in `localStorage`**, so a
 * session survives a reload and a closed tab; tests use the in-memory store.
 *
 * The trade-off, decided here so nobody has to rediscover it: `localStorage` can be read by any
 * script on the page, so one XSS flaw would hand over a session, where memory would not. It was
 * chosen because the session must survive a reload and a closed tab for 30 minutes of inactivity.
 * What limits the exposure is that the server issues short-lived tokens (the idle timeout) and the
 * app swaps the token for a fresh one only while the user is active (see `useSessionKeepAlive`), so
 * a token that is stolen, or left behind in a browser, stops working 30 minutes after its last use.
 * Nothing else is stored: no password and no profile.
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

/** The key the persistent store keeps the token under. */
export const TOKEN_STORAGE_KEY = "metaldesk.web.token";

/**
 * A token store backed by browser storage, so the token outlives a reload and a closed tab. Storage
 * can be blocked, full or absent (a private window, a locked-down browser); then the token lives in
 * memory for as long as the page does and nothing throws. A change made in another tab of the same
 * browser (a sign-in, a refresh, a sign-out) is picked up through the `storage` event, so every tab
 * agrees about whether the user is signed in.
 */
export function createPersistentTokenStore(
  storage: Storage | undefined = safeLocalStorage(),
  key: string = TOKEN_STORAGE_KEY,
): TokenStore {
  let fallback: string | null = null;
  const listeners = new Set<() => void>();
  const notify = () => {
    listeners.forEach((listener) => {
      listener();
    });
  };
  const read = (): string | null => {
    try {
      return storage ? storage.getItem(key) : fallback;
    } catch {
      return fallback;
    }
  };
  const write = (token: string | null) => {
    fallback = token;
    try {
      if (!storage) return;
      if (token === null) storage.removeItem(key);
      else storage.setItem(key, token);
    } catch {
      // Blocked or full: the in-memory copy still serves this page.
    }
  };
  return {
    get: read,
    set: (next) => {
      write(next);
      notify();
    },
    clear: () => {
      if (read() !== null) {
        write(null);
        notify();
      }
    },
    subscribe: (listener) => {
      listeners.add(listener);
      const onStorage = (event: StorageEvent) => {
        if (event.key === key || event.key === null) listener();
      };
      window.addEventListener("storage", onStorage);
      return () => {
        listeners.delete(listener);
        window.removeEventListener("storage", onStorage);
      };
    },
  };
}

function safeLocalStorage(): Storage | undefined {
  try {
    return window.localStorage;
  } catch {
    return undefined;
  }
}
