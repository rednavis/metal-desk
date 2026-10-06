import { useContext, useSyncExternalStore } from "react";
import { TokenStoreContext } from "./tokenContext";

/** Whether the user holds a token right now; false outside a {@link TokenStoreContext} provider. */
export function useSignedIn(): boolean {
  const store = useContext(TokenStoreContext);
  return useSyncExternalStore(
    (listener) => store?.subscribe(listener) ?? (() => undefined),
    () => store?.get() != null,
  );
}
