import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "react-router/dom";
import { ApiContext } from "./api/apiContext";
import { TokenStoreContext } from "./api/tokenContext";
import type { App as AppParts } from "./app/createApp";

/** The running app: the query cache, the API client and the router, provided once, here. */
function App({ app }: { app: AppParts }) {
  return (
    <QueryClientProvider client={app.queryClient}>
      <ApiContext.Provider value={app.client}>
        <TokenStoreContext.Provider value={app.tokenStore}>
          <RouterProvider router={app.router} />
        </TokenStoreContext.Provider>
      </ApiContext.Provider>
    </QueryClientProvider>
  );
}

export default App;
