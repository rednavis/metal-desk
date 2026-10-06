import { QueryClient } from "@tanstack/react-query";
import { createBrowserRouter, createMemoryRouter } from "react-router";
import { createApiClient, type ApiClientOptions } from "../api/client";
import { createMemoryTokenStore, createPersistentTokenStore } from "../api/tokenStore";
import { appRoutes } from "../routes/routes";

export interface AppOptions {
  /** Use an in-memory history starting here instead of the browser's; for tests. */
  initialEntries?: string[];
  fetchImpl?: ApiClientOptions["fetchImpl"];
  baseUrl?: string;
}

/**
 * Builds the pieces of the running app and wires them together: the router, the API client, the
 * token store and the query cache. Nothing here renders, so tests build the very same app `main.tsx` does, with a
 * memory history and a stubbed `fetch`. See `api/tokenStore.ts` for where the token lives.
 */
export function createApp(options: AppOptions = {}) {
  const router = options.initialEntries
    ? createMemoryRouter(appRoutes, { initialEntries: options.initialEntries })
    : createBrowserRouter(appRoutes);
  // Tests (which run on a memory history) keep the token in memory; the real app persists it.
  const tokenStore = options.initialEntries
    ? createMemoryTokenStore()
    : createPersistentTokenStore();
  const client = createApiClient({
    baseUrl: options.baseUrl,
    fetchImpl: options.fetchImpl,
    tokenStore,
  });
  // A 4xx will not get better by asking again, so only a transient failure is retried once.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 1, staleTime: 5_000, refetchOnWindowFocus: false } },
  });
  return { router, client, queryClient, tokenStore };
}

export type App = ReturnType<typeof createApp>;
