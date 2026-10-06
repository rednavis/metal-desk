import { useQuery } from "@tanstack/react-query";
import { useApi } from "../api/useApi";
import { currencyOptionsViewSchema } from "../api/types";

/** The currencies prices can be shown in, with the settlement currency and whether the rates are demo rates. */
export function useCurrencyOptions() {
  const client = useApi();
  return useQuery({
    queryKey: ["currencies"],
    queryFn: () => client.get("/currencies", { schema: currencyOptionsViewSchema }),
    staleTime: Infinity,
  });
}
