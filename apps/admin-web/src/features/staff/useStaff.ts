import { useQuery } from "@tanstack/react-query";
import { staffViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";

/**
 * Who the back office is acting as, as the server resolved it from the proxy's identity. The screens
 * send no credential of their own; this only shows the staff member whose identity the server sees.
 */
export function useStaff() {
  const client = useApi();
  return useQuery({
    queryKey: ["staff", "me"],
    queryFn: () => client.get("/admin/me", { schema: staffViewSchema }),
    retry: false,
  });
}
