import { useEffect, useRef } from "react";
import { useNavigate } from "react-router";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, Spinner } from "../../ui";
import { assertNever, type PaymentOutcome } from "./outcome";

interface PaymentFlowRouterProps {
  checkoutId: string;
  outcome: PaymentOutcome;
  /** What the server logged the attempt under; shown if the payment failed. */
  correlationId?: string;
  /** Called once an order is placed (captured or invoiced); the default opens the confirmation. */
  onPlaced?: () => Promise<void> | void;
  /** Called when the customer asks to try again after a failure. */
  onRetry?: () => void;
  /** Leaves the app for the provider's page; replaced in tests. */
  redirect?: (url: string) => void;
}

function leave(url: string): void {
  window.location.assign(url);
}

/**
 * Sends each payment outcome where BRD FR-7.2 says it goes, and is exhaustive over them (see
 * {@link PaymentOutcome}):
 *
 * - **captured** and **documentIssued** are successes: on to the confirmation, which tells them apart.
 * - **redirect** leaves for the provider. Nothing needs persisting first: the server's session holds
 *   the order and the provider's reference, and the provider returns the customer to
 *   `/checkout/<id>/return`, which carries the session's id and asks the server to verify.
 * - **element** mounts the provider's embedded form.
 * - **declined** returns to the payment method with everything kept, and the reason.
 * - **failed** stays here with what went wrong and the reference to quote.
 */
export function PaymentFlowRouter({
  checkoutId,
  outcome,
  correlationId,
  onPlaced,
  onRetry,
  redirect = leave,
}: PaymentFlowRouterProps) {
  const { t } = usePreferences();
  const navigate = useNavigate();
  const handled = useRef<PaymentOutcome | null>(null);
  const checkoutPath = `/checkout/${encodeURIComponent(checkoutId)}`;

  useEffect(() => {
    if (handled.current === outcome) return;
    handled.current = outcome;
    switch (outcome.kind) {
      case "captured":
      case "documentIssued":
        void (onPlaced ?? (() => navigate({ pathname: checkoutPath, search: "?step=5" })))();
        break;
      case "redirect":
        redirect(outcome.url);
        break;
      case "declined":
        void navigate(
          { pathname: checkoutPath, search: "?step=3" },
          { state: { decline: outcome.reason } },
        );
        break;
      case "element":
      case "failed":
        break;
      default:
        assertNever(outcome);
    }
  }, [outcome, onPlaced, redirect, navigate, checkoutPath]);

  switch (outcome.kind) {
    case "captured":
      return <Spinner label={t("checkout.confirmation.PAID")} />;
    case "documentIssued":
      return <Spinner label={t("checkout.confirmation.INVOICE")} />;
    case "redirect":
      return <Spinner label={t("checkout.redirecting")} />;
    case "declined":
      return (
        <section role="alert">
          <h2>{t("checkout.decline.title")}</h2>
          <p>{t("checkout.decline.body", { reason: outcome.reason })}</p>
        </section>
      );
    case "element":
      return (
        <PaymentElement
          clientHandle={outcome.clientHandle}
          onComplete={(reference) => {
            void navigate({
              pathname: `${checkoutPath}/return`,
              search: `?reference=${encodeURIComponent(reference)}`,
            });
          }}
        />
      );
    case "failed":
      return (
        <section role="alert" className="md-error-state">
          <h2>{t("checkout.error.title")}</h2>
          <p>{outcome.message}</p>
          <p>
            {t("error.reference", {
              reference: correlationId ?? checkoutId,
              code: outcome.code,
            })}
          </p>
          {onRetry ? <Button onClick={onRetry}>{t("checkout.error.retry")}</Button> : null}
        </section>
      );
    default:
      return assertNever(outcome);
  }
}

/**
 * Stands in for a provider's embedded payment form. No vendor SDK is bundled (ADR-0002): it shows
 * where the real element mounts, given the handle the server returned, and reports completion the
 * way the real one would, by handing back the provider's reference.
 */
function PaymentElement({
  clientHandle,
  onComplete,
}: {
  clientHandle: string;
  onComplete: (reference: string) => void;
}) {
  const { t } = usePreferences();
  return (
    <section
      className="md-panel md-payment-element"
      aria-labelledby="payment-element-title"
      data-handle={clientHandle}
    >
      <h2 id="payment-element-title">{t("checkout.element.title")}</h2>
      <p>{t("checkout.element.note")}</p>
      <Button
        onClick={() => {
          onComplete(clientHandle);
        }}
      >
        {t("checkout.element.complete")}
      </Button>
    </section>
  );
}
