import { useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router";
import { paymentResultViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, ErrorState, Spinner } from "../../ui";
import { toOutcome } from "./outcome";
import { PaymentFlowRouter } from "./PaymentFlowRouter";
import "./checkout.css";

/**
 * Where a provider sends the customer back to, `/checkout/:checkoutId/return?reference=...`.
 *
 * What the address says is a claim, not a result: the only things read from it are the session's id
 * and the provider's reference, and the outcome is whatever the server gets by asking the provider to
 * confirm that reference. A `status=success` parameter, or anything else, is never looked at.
 */
export function PaymentReturnRoute() {
  const { t } = usePreferences();
  const client = useApi();
  const { checkoutId = "" } = useParams();
  const [params] = useSearchParams();
  const reference = params.get("reference") ?? "";
  const correlationId = useMemo(() => crypto.randomUUID(), []);

  const result = useQuery({
    queryKey: ["checkout", checkoutId, "callback", reference],
    queryFn: () =>
      client.get("/checkout/payment/callback", {
        query: { checkout: checkoutId, reference },
        schema: paymentResultViewSchema,
        correlationId,
      }),
    retry: false,
    staleTime: Infinity,
  });
  const navigate = useNavigate();
  const back = () => {
    void navigate({ pathname: `/checkout/${encodeURIComponent(checkoutId)}`, search: "?step=3" });
  };

  return (
    <>
      <h1>{t("page.checkout.title")}</h1>
      {result.isPending ? <Spinner label={t("checkout.return.verifying")} /> : null}
      {result.isError ? (
        <ErrorState
          error={result.error}
          action={<Button onClick={back}>{t("checkout.cancelled.action")}</Button>}
        />
      ) : null}
      {result.data ? (
        <PaymentFlowRouter
          checkoutId={checkoutId}
          outcome={toOutcome(result.data)}
          correlationId={correlationId}
          onRetry={back}
        />
      ) : null}
    </>
  );
}

/** Where a provider sends the customer who cancelled: nothing was charged, and every step is still there. */
export function PaymentCancelRoute() {
  const { t } = usePreferences();
  const { checkoutId = "" } = useParams();
  return (
    <>
      <h1>{t("checkout.cancelled.title")}</h1>
      <p>{t("checkout.cancelled.body")}</p>
      <Link to={{ pathname: `/checkout/${encodeURIComponent(checkoutId)}`, search: "?step=3" }}>
        {t("checkout.cancelled.action")}
      </Link>
    </>
  );
}
