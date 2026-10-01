import { useQuery } from "@tanstack/react-query";
import { staffViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";

/**
 * Who the back office is acting as, as the server resolved it from the bearer token. The server
 * decides: a user who has been removed or disabled since signing in is answered with a 401 here.
 */
export function useStaff(enabled = true) {
  const client = useApi();
  return useQuery({
    queryKey: ["staff", "me"],
    queryFn: () => client.get("/admin/me", { schema: staffViewSchema }),
    enabled,
    retry: false,
  });
}
