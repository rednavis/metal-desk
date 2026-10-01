import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  confirmationViewSchema,
  deliveryEvaluationViewSchema,
  handoffViewSchema,
  overviewViewSchema,
  paymentMethodsViewSchema,
  paymentResultViewSchema,
  sessionViewSchema,
  step1ResponseSchema,
  type PaymentMethod,
  type Step1Request,
} from "../../api/types";
import { useApi } from "../../api/useApi";
import { toOutcome, type PaymentOutcome } from "./outcome";
import { reachableStep } from "./steps";

/** Every query of one checkout shares this prefix, so an edit can drop what it made stale in one call. */
const prefix = (checkoutId: string) => ["checkout", checkoutId] as const;

/**
 * One checkout, from the server's point of view. The session, the methods on offer and the
 * confirmation are queries; each step's action is a mutation that resolves only once the session
 * has been fetched again, so the step that follows it already sees what the server now allows. What
 * is reachable is {@link reachableStep} of that, never client state, so a reload resumes correctly.
 *
 * Editing step 1 makes the server drop the delivery evaluation; here the methods, overview and
 * confirmation that depended on it are dropped too, so no stale quote or method can be shown.
 */
export function useCheckoutSession(checkoutId: string) {
  const client = useApi();
  const queryClient = useQueryClient();
  const base = `/checkout/sessions/${encodeURIComponent(checkoutId)}`;

  const session = useQuery({
    queryKey: [...prefix(checkoutId), "session"],
    queryFn: () => client.get(base, { schema: sessionViewSchema }),
  });
  const methods = useQuery({
    queryKey: [...prefix(checkoutId), "methods"],
    queryFn: () => client.get(`${base}/payment/methods`, { schema: paymentMethodsViewSchema }),
    enabled: session.data?.delivery?.stage === "PAYMENT_ALLOWED",
  });
  const selected = methods.data?.selected;
  // A 409 here means "nothing has been placed yet"; it is an answer, not something to retry.
  const confirmation = useQuery({
    queryKey: [...prefix(checkoutId), "confirmation"],
    queryFn: () => client.get(`${base}/confirmation`, { schema: confirmationViewSchema }),
    enabled: selected !== undefined,
    retry: false,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: prefix(checkoutId) });
  const dropDependents = () => {
    for (const name of ["methods", "overview", "confirmation"]) {
      queryClient.removeQueries({ queryKey: [...prefix(checkoutId), name] });
    }
  };

  const submitStep1 = useMutation({
    mutationFn: (request: Step1Request) =>
      client.put(`${base}/step1`, { body: request, schema: step1ResponseSchema }),
    onSuccess: async () => {
      dropDependents();
      await refresh();
    },
  });
  const evaluate = useMutation({
    mutationFn: () =>
      client.post(`${base}/delivery/evaluate`, { schema: deliveryEvaluationViewSchema }),
    onSuccess: refresh,
  });
  const handoff = useMutation({
    mutationFn: () => client.post(`${base}/handoff`, { schema: handoffViewSchema }),
    onSuccess: async () => {
      // The server removed the cart the order was made from; the badge and the cart page must follow.
      void queryClient.invalidateQueries({ queryKey: ["cart"] });
      await refresh();
    },
  });
  const selectMethod = useMutation({
    mutationFn: (method: PaymentMethod) =>
      client.put(`${base}/payment/method`, { body: { method }, schema: paymentMethodsViewSchema }),
    onSuccess: (view) => {
      queryClient.setQueryData([...prefix(checkoutId), "methods"], view);
    },
  });
  const execute = useMutation({
    mutationFn: async (
      confirmedTotal: string,
    ): Promise<{
      outcome: PaymentOutcome;
      correlationId: string;
    }> => {
      // Chosen here so that a failed payment can show the id the server logged it under.
      const correlationId = crypto.randomUUID();
      const view = await client.post(`${base}/payment/execute`, {
        body: { confirmedTotal },
        schema: paymentResultViewSchema,
        correlationId,
      });
      return { outcome: toOutcome(view), correlationId };
    },
    onError: () => queryClient.invalidateQueries({ queryKey: [...prefix(checkoutId), "overview"] }),
  });

  const loaded = session.data;
  const needsMethods = loaded?.delivery?.stage === "PAYMENT_ALLOWED";
  const settled =
    session.isSuccess &&
    (!needsMethods || !methods.isLoading) &&
    (selected === undefined || !confirmation.isLoading);

  return {
    checkoutId,
    session,
    methods,
    confirmation,
    /** Everything the step gating depends on has been answered. */
    settled,
    reachable: loaded ? reachableStep(loaded, selected, confirmation.isSuccess) : 1,
    submitStep1,
    evaluate,
    handoff,
    selectMethod,
    execute,
  };
}

export type CheckoutApi = ReturnType<typeof useCheckoutSession>;

/** The final overview (BRD FR-7.1), always fetched fresh: its total is the amount that will be charged. */
export function useOverview(checkoutId: string) {
  const client = useApi();
  return useQuery({
    queryKey: [...prefix(checkoutId), "overview"],
    queryFn: () =>
      client.get(`/checkout/sessions/${encodeURIComponent(checkoutId)}/overview`, {
        schema: overviewViewSchema,
      }),
    staleTime: 0,
    gcTime: 0,
  });
}
