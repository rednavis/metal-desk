import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  orderDetailViewSchema,
  pageViewSchema,
  orderSummaryViewSchema,
  quoteOutcomeSchema,
  type ManagerQuoteRequest,
  type OrderStatus,
} from "../../api/types";
import { useApi } from "../../api/useApi";

const orderPageSchema = pageViewSchema(orderSummaryViewSchema);

export const PAGE_SIZE = 20;

/** One page of orders, optionally only those in one status. */
export function useOrderPage(status: OrderStatus | undefined, page: number) {
  const client = useApi();
  return useQuery({
    queryKey: ["orders", "list", status ?? "all", page],
    queryFn: () =>
      client.get("/admin/orders", {
        query: { status, page, size: PAGE_SIZE },
        schema: orderPageSchema,
      }),
  });
}

/** One page of the orders waiting for a manager's quote. */
export function useQuotePage(page: number) {
  const client = useApi();
  return useQuery({
    queryKey: ["quotes", "list", page],
    queryFn: () =>
      client.get("/admin/quotes", { query: { page, size: PAGE_SIZE }, schema: orderPageSchema }),
  });
}

/** One order in full: what staff see, and the triggers the server accepts for it now. */
export function useOrder(id: string) {
  const client = useApi();
  return useQuery({
    queryKey: ["orders", "one", id],
    queryFn: () =>
      client.get(`/admin/orders/${encodeURIComponent(id)}`, { schema: orderDetailViewSchema }),
  });
}

/** A handed-off order, with the context staff price it from. */
export function useQuote(orderId: string) {
  const client = useApi();
  return useQuery({
    queryKey: ["orders", "one", orderId],
    queryFn: () =>
      client.get(`/admin/quotes/${encodeURIComponent(orderId)}`, { schema: orderDetailViewSchema }),
  });
}

/** What staff can do to an order. Every change refetches orders and quotes: one decision moves an order between lists. */
export function useOrderActions(orderId: string) {
  const client = useApi();
  const queryClient = useQueryClient();
  const base = `/admin/orders/${encodeURIComponent(orderId)}`;
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["orders"] }),
      queryClient.invalidateQueries({ queryKey: ["quotes"] }),
    ]);
  };
  const advance = (path: string) =>
    client.post(`${base}/${path}`, { schema: orderDetailViewSchema });
  const startFulfillment = useMutation({
    mutationFn: () => advance("fulfillment"),
    onSuccess: refresh,
  });
  const markPaymentReceived = useMutation({
    mutationFn: () => advance("payment-received"),
    onSuccess: refresh,
  });
  const markDelivered = useMutation({ mutationFn: () => advance("delivery"), onSuccess: refresh });
  const enterShipment = useMutation({
    mutationFn: (entry: { carrier: string; trackingReference: string }) =>
      client.put(`${base}/shipment`, { body: entry, schema: orderDetailViewSchema }),
    onSuccess: refresh,
  });
  const setTerms = useMutation({
    mutationFn: (request: ManagerQuoteRequest) =>
      client.post(`/admin/quotes/${encodeURIComponent(orderId)}/terms`, {
        body: request,
        schema: quoteOutcomeSchema,
      }),
    onSuccess: refresh,
  });
  const decline = useMutation({
    mutationFn: (reason: string) =>
      client.post(`/admin/quotes/${encodeURIComponent(orderId)}/decline`, {
        body: { reason },
        schema: quoteOutcomeSchema,
      }),
    onSuccess: refresh,
  });
  return { startFulfillment, markPaymentReceived, markDelivered, enterShipment, setTerms, decline };
}
