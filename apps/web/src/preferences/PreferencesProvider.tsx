import type { QueryClient } from "@tanstack/react-query";
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from "react";
import type { ApiClient } from "../api/client";
import type { RequestContext } from "../api/requestContext";
import type { TokenStore } from "../api/tokenStore";
import { createFormatter } from "../i18n/format";
import type { Locale } from "../i18n/locales";
import { createTranslator } from "../i18n/translate";
import { applyTheme, type Theme } from "../theme/theme";
import { defaultPreferences, type Preferences } from "./model";
import { PreferencesContext, type PreferencesValue } from "./PreferencesContext";
import {
  fetchServerPreferences,
  loadLocal,
  reconcile,
  saveLocal,
  saveServerPreferences,
  serverIsMissingSomething,
} from "./persistence";

interface PreferencesProviderProps {
  client: ApiClient;
  tokenStore: TokenStore;
  queryClient: QueryClient;
  requestContext: RequestContext;
  /** Where an anonymous visitor's choice is kept; the browser's `localStorage` by default. */
  storage?: Storage | null;
  children: ReactNode;
}

function browserStorage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

/**
 * The single source of theme, language and currency for the whole app, mounted once at the root.
 *
 * It applies the theme to the page, tells the API client which language and currency to use,
 * persists every change (locally always, to the server too when signed in), and on sign-in
 * reconciles with the server copy under the rule documented in `persistence.ts`. A change of
 * language or currency invalidates every cached query, so what is on screen is refetched and
 * re-rendered, without a reload.
 */
export function PreferencesProvider({
  client,
  tokenStore,
  queryClient,
  requestContext,
  storage = browserStorage(),
  children,
}: PreferencesProviderProps) {
  const [preferences, setPreferences] = useState<Preferences>(() => {
    const initial = { ...defaultPreferences(navigator.languages), ...loadLocal(storage) };
    requestContext.set({ locale: initial.locale, currency: initial.currency });
    return initial;
  });
  const signedIn = useSyncExternalStore(tokenStore.subscribe, () => tokenStore.get() !== null);

  const apply = useCallback(
    (next: Preferences, previous: Preferences) => {
      requestContext.set({ locale: next.locale, currency: next.currency });
      saveLocal(storage, next);
      setPreferences(next);
      if (next.locale !== previous.locale || next.currency !== previous.currency) {
        void queryClient.invalidateQueries();
      }
    },
    [queryClient, requestContext, storage],
  );

  // The page follows the theme, and follows the device too while the choice is "system".
  useEffect(() => {
    applyTheme(preferences.theme);
    document.documentElement.lang = preferences.locale;
    if (preferences.theme !== "system" || typeof window.matchMedia !== "function") return;
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    const follow = () => {
      applyTheme("system");
    };
    query.addEventListener("change", follow);
    return () => {
      query.removeEventListener("change", follow);
    };
  }, [preferences.theme, preferences.locale]);

  // On sign-in, the server's copy wins field by field; what only this browser has is pushed up.
  useEffect(() => {
    if (!signedIn) return;
    let cancelled = false;
    void (async () => {
      try {
        const server = await fetchServerPreferences(client);
        if (cancelled) return;
        const merged = reconcile(preferences, server);
        apply(merged, preferences);
        if (serverIsMissingSomething(merged, server)) await saveServerPreferences(client, merged);
      } catch {
        // The server copy is unreachable: keep working with the local one, sync on the next sign-in.
      }
    })();
    return () => {
      cancelled = true;
    };
    // Reconciling is a sign-in event: it must not re-run when the preferences it just set change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signedIn, client]);

  const change = useCallback(
    (patch: Partial<Preferences>) => {
      const next = { ...preferences, ...patch };
      apply(next, preferences);
      if (tokenStore.get() !== null)
        void saveServerPreferences(client, next).catch(() => undefined);
    },
    [apply, client, preferences, tokenStore],
  );

  const value = useMemo<PreferencesValue>(
    () => ({
      ...preferences,
      setTheme: (theme: Theme) => {
        change({ theme });
      },
      setLocale: (locale: Locale) => {
        change({ locale });
      },
      setCurrency: (currency: string) => {
        change({ currency });
      },
      t: createTranslator(preferences.locale),
      format: createFormatter(preferences.locale),
    }),
    [preferences, change],
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}
