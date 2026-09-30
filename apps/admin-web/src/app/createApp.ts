import { QueryClient } from "@tanstack/react-query";
import { createBrowserRouter, createMemoryRouter } from "react-router";
import { createApiClient, type ApiClientOptions } from "../api/client";
import { appRoutes } from "../routes/routes";

export interface AppOptions {
  /** Use an in-memory history starting here instead of the browser's; for tests. */
  initialEntries?: string[];
  fetchImpl?: ApiClientOptions["fetchImpl"];
  baseUrl?: string;
}

/**
 * Builds the pieces of the running app and wires them together: the router, the API client and the
 * query cache. Nothing here renders, so tests build the very same app `main.tsx` does, with a
 * memory history and a stubbed `fetch`. There is no token store and no sign-in: see `api/client.ts`.
 */
export function createApp(options: AppOptions = {}) {
  const router = options.initialEntries
    ? createMemoryRouter(appRoutes, { initialEntries: options.initialEntries })
    : createBrowserRouter(appRoutes);
  const client = createApiClient({ baseUrl: options.baseUrl, fetchImpl: options.fetchImpl });
  // A 4xx will not get better by asking again, so only a transient failure is retried once.
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: 1, staleTime: 5_000, refetchOnWindowFocus: false } },
  });
  return { router, client, queryClient };
}

export type App = ReturnType<typeof createApp>;
