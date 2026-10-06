import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useMemo, useState, type ReactNode } from "react";
import { cartViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { CartContext, type CartFailure, type CartState } from "./CartContext";

type Change =
  | { kind: "add"; productId: string }
  | { kind: "quantity"; productId: string; quantity: number }
  | { kind: "remove"; productId: string };

/**
 * The cart, sourced from the server and only from the server.
 *
 * Every change is a request, and what is shown afterwards is the cart that request returned: the
 * server decides what a repeat add does (a no-op, T-034) and what an over-cap quantity does
 * (refused, never clamped), so nothing here predicts the result, and the badge can never disagree
 * with the cart. That is the "server-response only" strategy, with no optimistic step to reconcile.
 *
 * The cart is in the display currency (part of the query key), so switching currency fetches it
 * again, converted by the server.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const client = useApi();
  const queryClient = useQueryClient();
  const { currency, locale } = usePreferences();
  const key = useMemo(() => ["cart", currency, locale] as const, [currency, locale]);
  const [failure, setFailure] = useState<CartFailure | undefined>();

  const query = useQuery({
    queryKey: key,
    queryFn: () => client.get("/cart", { schema: cartViewSchema }),
  });

  const mutation = useMutation({
    mutationFn: (change: Change) => {
      const options = { schema: cartViewSchema };
      const line = (id: string) => `/cart/lines/${encodeURIComponent(id)}`;
      switch (change.kind) {
        case "add":
          return client.post("/cart/lines", { ...options, body: { productId: change.productId } });
        case "quantity":
          return client.put(line(change.productId), {
            ...options,
            body: { quantity: change.quantity },
          });
        case "remove":
          return client.delete(line(change.productId), options);
      }
    },
    onSuccess: (view) => {
      setFailure(undefined);
      queryClient.setQueryData(key, view);
      // The same cart in another currency or language is now out of date; it refetches if it is used.
      void queryClient.invalidateQueries({ queryKey: ["cart"], refetchType: "none" });
    },
  });
  const { mutateAsync } = mutation;

  const run = useCallback(
    async (change: Change) => {
      setFailure(undefined);
      try {
        await mutateAsync(change);
      } catch (error) {
        setFailure({ productId: change.productId, error });
      }
    },
    [mutateAsync],
  );
  const { refetch } = query;

  const state = useMemo<CartState>(
    () => ({
      cart: query.data,
      isLoading: query.isPending,
      loadError: query.data === undefined ? query.error : null,
      reload: () => {
        void refetch();
      },
      busy: mutation.isPending,
      failure,
      add: (productId) => run({ kind: "add", productId }),
      setQuantity: (productId, quantity) => run({ kind: "quantity", productId, quantity }),
      remove: (productId) => run({ kind: "remove", productId }),
    }),
    [query.data, query.isPending, query.error, refetch, mutation.isPending, failure, run],
  );

  return <CartContext.Provider value={state}>{children}</CartContext.Provider>;
}
