import { useQueryClient } from "@tanstack/react-query";
import { useContext, useMemo, useState, type ReactNode } from "react";
import { TokenStoreContext } from "../../api/tokenContext";
import { customerViewSchema, signInResponseSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { useSignedIn } from "../../api/useSignedIn";
import { ME_KEY } from "./meKey";
import { AuthContext, type AuthState, type KnownAccount } from "./AuthContext";

/**
 * Signing in and out and switching accounts, and what each does to the data on screen.
 *
 * - **Sign in** stores the token, learns who it is for, and refreshes the cart: a cart held before
 *   signing in is merged into the customer's by the server on the first signed-in request.
 * - **Sign out** discards the token and everything scoped to the account (who-am-I, orders, a
 *   checkout session) but **never the cart**: the cart belongs to its owner, not to a token, and the
 *   server keeps it (FR-2.7). It is only fetched again, so the screen shows what the server holds.
 * - **Switch** replaces the token with the one the server mints for the target and resets every
 *   query, so nothing the previous account could see survives the switch (BRD FR-2.5).
 *
 * The accounts offered for switching are the ones signed in to earlier in this tab: there is no
 * server endpoint that lists an account's linked accounts, and the server decides whether a switch
 * is allowed in any case.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useApi();
  const queryClient = useQueryClient();
  const tokenStore = useContext(TokenStoreContext);
  const signedIn = useSignedIn();
  const [known, setKnown] = useState<KnownAccount[]>([]);
  const [currentId, setCurrentId] = useState<string | undefined>();

  const state = useMemo<AuthState>(() => {
    if (tokenStore === null) throw new Error("AuthProvider needs a TokenStoreContext");

    async function identify(label: string): Promise<void> {
      const me = await queryClient.fetchQuery({
        queryKey: ME_KEY,
        queryFn: () => client.get("/auth/me", { schema: customerViewSchema }),
        staleTime: 0,
      });
      setCurrentId(me.customerId);
      setKnown((accounts) => [
        ...accounts.filter((account) => account.customerId !== me.customerId),
        { customerId: me.customerId, label },
      ]);
    }

    return {
      signedIn,
      currentId,
      otherAccounts: known.filter((account) => account.customerId !== currentId),
      signIn: async (identifier, password) => {
        const response = await client.post("/auth/sign-in", {
          body: { identifier, password },
          schema: signInResponseSchema,
        });
        tokenStore.set(response.accessToken);
        await identify(identifier);
        await queryClient.invalidateQueries({ queryKey: ["cart"] });
      },
      signOut: async () => {
        try {
          await client.post("/auth/sign-out");
        } catch {
          // The token is stateless and is discarded either way.
        }
        tokenStore.clear();
        setCurrentId(undefined);
        for (const scope of ["auth", "orders", "checkout"]) {
          queryClient.removeQueries({ queryKey: [scope] });
        }
        await queryClient.invalidateQueries({ queryKey: ["cart"] });
      },
      switchTo: async (customerId) => {
        const response = await client.post("/account/switch", {
          body: { targetCustomerId: customerId },
          schema: signInResponseSchema,
        });
        tokenStore.set(response.accessToken);
        await queryClient.resetQueries();
        await identify(known.find((account) => account.customerId === customerId)?.label ?? "");
      },
    };
  }, [client, queryClient, tokenStore, signedIn, known, currentId]);

  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}
