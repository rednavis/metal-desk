import { useQuery } from "@tanstack/react-query";
import { useApi } from "../../api/useApi";
import { cartViewSchema } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";

/**
 * The cart, in the customer's display currency. The currency is part of the query key, so a switch
 * is a different query and the cart is fetched again, converted by the server (BRD FR-1.8).
 */
export function useCart() {
  const client = useApi();
  const { currency, locale } = usePreferences();
  return useQuery({
    queryKey: ["cart", currency, locale],
    queryFn: () => client.get("/cart", { schema: cartViewSchema }),
  });
}
