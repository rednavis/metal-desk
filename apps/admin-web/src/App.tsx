import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router/dom";
import { ApiContext } from "./api/apiContext";
import { TokenStoreContext } from "./api/tokenContext";
import { AuthProvider } from "./features/auth/AuthProvider";
import type { App as AppParts } from "./app/createApp";

/** The running app: the query cache, the token store, the API client, the
 * sign-in state and the router, provided once, here. */
function App({ app }: { app: AppParts }) {
  return (
    <QueryClientProvider client={app.queryClient}>
      <TokenStoreContext.Provider value={app.tokenStore}>
        <ApiContext.Provider value={app.client}>
          <AuthProvider>
            <RouterProvider router={app.router} />
          </AuthProvider>
        </ApiContext.Provider>
      </TokenStoreContext.Provider>
    </QueryClientProvider>
  );
}

export default App;
