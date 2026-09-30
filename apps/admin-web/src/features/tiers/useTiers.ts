import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";
import {
  tierRemovalSchema,
  tierResultSchema,
  tierViewSchema,
  type TierRequest,
} from "../../api/types";
import { useApi } from "../../api/useApi";

const tiersSchema = z.array(tierViewSchema);

/** All tiers, or one region's. */
export function useTiers(region?: string, enabled = true) {
  const client = useApi();
  return useQuery({
    queryKey: ["tiers", region ?? "all"],
    queryFn: () => client.get("/admin/tiers", { query: { region }, schema: tiersSchema }),
    enabled,
  });
}

/** One tier. */
export function useTier(id: string | undefined) {
  const client = useApi();
  return useQuery({
    queryKey: ["tiers", "one", id],
    queryFn: () =>
      client.get(`/admin/tiers/${encodeURIComponent(id ?? "")}`, { schema: tierViewSchema }),
    enabled: id !== undefined,
  });
}

/** Creating, changing and removing tiers; each drops every cached tier list, since coverage depends on all of them. */
export function useTierMutations() {
  const client = useApi();
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["tiers"] });
  const save = useMutation({
    mutationFn: ({ id, request }: { id?: string; request: TierRequest }) =>
      id === undefined
        ? client.post("/admin/tiers", { body: request, schema: tierResultSchema })
        : client.put(`/admin/tiers/${encodeURIComponent(id)}`, {
            body: request,
            schema: tierResultSchema,
          }),
    onSuccess: refresh,
  });
  const remove = useMutation({
    mutationFn: (id: string) =>
      client.delete(`/admin/tiers/${encodeURIComponent(id)}`, { schema: tierRemovalSchema }),
    onSuccess: refresh,
  });
  return { save, remove };
}
