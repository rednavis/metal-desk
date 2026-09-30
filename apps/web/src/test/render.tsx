import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, type RenderResult } from "@testing-library/react";
import { type ReactNode } from "react";
import App from "../App";
import { ApiContext } from "../api/apiContext";
import { createApp } from "../app/createApp";
import { createRequestContext } from "../api/requestContext";
import { createApiClient } from "../api/client";
import { createMemoryTokenStore } from "../api/tokenStore";
import { PreferencesProvider } from "../preferences/PreferencesProvider";

/** An in-memory `Storage`, so a test can "reload" by rendering again with the same one. */
export function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    get length() {
      return data.size;
    },
    clear: () => {
      data.clear();
    },
    getItem: (key) => data.get(key) ?? null,
    key: (index) => [...data.keys()][index] ?? null,
    removeItem: (key) => {
      data.delete(key);
    },
    setItem: (key, value) => {
      data.set(key, value);
    },
  };
}

/** Makes the device ask for a dark or light interface. */
export function stubDeviceTheme(dark: boolean): void {
  window.matchMedia = ((query: string) => ({
    matches: dark && query.includes("dark"),
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })) as unknown as typeof window.matchMedia;
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status });
}

export interface RenderedApp extends RenderResult {
  app: ReturnType<typeof createApp>;
}

/** Renders the whole storefront, as `main.tsx` does, with a memory history and a stubbed network. */
export function renderApp(options: {
  path?: string;
  fetchImpl?: typeof globalThis.fetch;
  storage?: Storage;
}): RenderedApp {
  const app = createApp({
    initialEntries: [options.path ?? "/"],
    baseUrl: "/api",
    fetchImpl: options.fetchImpl ?? (() => Promise.resolve(json({}, 404))),
  });
  const result = render(
    <PreferencesProvider
      client={app.client}
      tokenStore={app.tokenStore}
      queryClient={app.queryClient}
      requestContext={app.requestContext}
      storage={options.storage ?? memoryStorage()}
    >
      <App app={app} />
    </PreferencesProvider>,
  );
  return { ...result, app };
}

/** Renders a component under the preference provider and a client, without the router. */
export function renderWithPreferences(
  ui: ReactNode,
  options: { fetchImpl?: typeof globalThis.fetch; storage?: Storage; signedIn?: string } = {},
) {
  const tokenStore = createMemoryTokenStore();
  const requestContext = createRequestContext({ locale: "en", currency: "EUR" });
  const client = createApiClient({
    baseUrl: "/api",
    fetchImpl: options.fetchImpl ?? (() => Promise.resolve(json({}, 404))),
    tokenStore,
    requestContext,
  });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ApiContext.Provider value={client}>
        <PreferencesProvider
          client={client}
          tokenStore={tokenStore}
          queryClient={queryClient}
          requestContext={requestContext}
          storage={options.storage ?? memoryStorage()}
        >
          {ui}
        </PreferencesProvider>
      </ApiContext.Provider>
    </QueryClientProvider>,
  );
  return { ...result, tokenStore, client, requestContext };
}
