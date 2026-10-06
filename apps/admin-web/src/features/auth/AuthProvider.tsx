import { useQueryClient } from "@tanstack/react-query";
import { useContext, useMemo, type ReactNode } from "react";
import { TokenStoreContext } from "../../api/tokenContext";
import { signInResponseSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { useSignedIn } from "../../api/useSignedIn";
import { AuthContext, type AuthState } from "./AuthContext";
import { useSessionKeepAlive } from "./useSessionKeepAlive";

/**
 * Signing in and out, and what each does to the data on screen.
 *
 * - **Sign in** stores the token. Which user it is for is learned by the first screen that asks the
 *   server (`/admin/me`), never taken from the sign-in response alone.
 * - **Sign out** tells the server, discards the token and removes every cached query, so nothing the
 *   previous user could see survives for the next one.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useApi();
  const queryClient = useQueryClient();
  const tokenStore = useContext(TokenStoreContext);
  const signedIn = useSignedIn();
  useSessionKeepAlive(client, tokenStore, signedIn);

  const state = useMemo<AuthState>(() => {
    if (tokenStore === null) throw new Error("AuthProvider needs a TokenStoreContext");
    return {
      signedIn,
      signIn: async (login, password) => {
        const response = await client.post("/admin/auth/sign-in", {
          body: { login, password },
          schema: signInResponseSchema,
        });
        queryClient.clear();
        tokenStore.set(response.accessToken);
      },
      signOut: async () => {
        try {
          await client.post("/admin/auth/sign-out");
        } catch {
          // The token is stateless and is discarded either way.
        }
        tokenStore.clear();
        queryClient.clear();
      },
    };
  }, [client, queryClient, tokenStore, signedIn]);

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}
