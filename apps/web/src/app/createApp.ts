import { QueryClient } from "@tanstack/react-query";
import { createBrowserRouter, createMemoryRouter } from "react-router";
import { isApiError } from "../api/errors";
import { createApiClient, type ApiClientOptions } from "../api/client";
import { createRequestContext } from "../api/requestContext";
import { createMemoryTokenStore } from "../api/tokenStore";
import { SIGN_IN_PATH, appRoutes } from "../routes/routes";

export interface AppOptions {
  /** Use an in-memory history starting here instead of the browser's; for tests. */
  initialEntries?: string[];
  fetchImpl?: ApiClientOptions["fetchImpl"];
  baseUrl?: string;
}

/**
 * Builds the pieces of the running app and wires them together: the router, the token store, the
 * API client (whose 401 handler routes to sign-in) and the query cache. Nothing here renders, so
 * tests build the very same app `main.tsx` does, with a memory history and a stubbed `fetch`.
 */
export function createApp(options: AppOptions = {}) {
  const tokenStore = createMemoryTokenStore();
  // The provider replaces these with the customer's real preferences before anything is requested.
  const requestContext = createRequestContext({ locale: "en", currency: "EUR" });
  const router = options.initialEntries
    ? createMemoryRouter(appRoutes, { initialEntries: options.initialEntries })
    : createBrowserRouter(appRoutes);
  const client = createApiClient({
    baseUrl: options.baseUrl,
    fetchImpl: options.fetchImpl,
    tokenStore,
    requestContext,
    onUnauthorized: () => {
      const from = router.state.location.pathname;
      if (from !== SIGN_IN_PATH) {
        void router.navigate(`${SIGN_IN_PATH}?from=${encodeURIComponent(from)}`);
      }
    },
  });
  // A 4xx will not get better by asking again; only transient failures are retried, and the
  // short refresh of FR-1.1 is a per-query `refetchInterval`, not a global default.
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failures, error) =>
          failures < 1 && !(isApiError(error) && error.status >= 400 && error.status < 500),
        staleTime: 5_000,
        refetchOnWindowFocus: false,
      },
    },
  });
  return { router, client, tokenStore, queryClient, requestContext };
}

export type App = ReturnType<typeof createApp>;
