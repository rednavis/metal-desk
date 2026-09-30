import { useState } from "react";
import { Link } from "react-router";
import { usePreferences } from "../../../preferences/usePreferences";
import { Button, ErrorState, Spinner } from "../../../ui";
import type { PaymentOutcome } from "../outcome";
import { PaymentFlowRouter } from "../PaymentFlowRouter";
import { useOverview, type CheckoutApi } from "../useCheckoutSession";

/**
 * Step 4 (BRD FR-7.1, FR-7.2): the final overview, and the action that completes the order.
 *
 * Every amount here is the server's: the overview's own totals are displayed as they arrive and
 * nothing is added up in the browser, because that total is the amount the provider will be asked
 * to charge, and the amount the customer confirms is sent back so the server can refuse to charge
 * anything else. Every earlier step can be edited from here.
 */
export function Step4Overview({
  checkout,
  goTo,
  onPlaced,
}: {
  checkout: CheckoutApi;
  goTo: (step: 1 | 2 | 3) => void;
  onPlaced: () => Promise<void>;
}) {
  const { t, format } = usePreferences();
  const overview = useOverview(checkout.checkoutId);
  const { execute } = checkout;
  const [attempt, setAttempt] = useState<{
    outcome: PaymentOutcome;
    correlationId: string;
  } | null>(null);

  if (overview.isPending) return <Spinner label={t("checkout.loading")} />;
  if (overview.isError) return <ErrorState error={overview.error} />;
  const { details, lines, delivery, totals, paymentMethod } = overview.data;

  if (attempt) {
    return (
      <PaymentFlowRouter
        checkoutId={checkout.checkoutId}
        outcome={attempt.outcome}
        correlationId={attempt.correlationId}
        onPlaced={onPlaced}
        onRetry={() => {
          setAttempt(null);
        }}
      />
    );
  }

  const invoice = checkout.methods.data?.methods.some(
    (offered) => offered.method === paymentMethod && offered.group === "INVOICE",
  );
  return (
    <section aria-labelledby="step4-title">
      <h2 id="step4-title">{t("checkout.step4.title")}</h2>

      <h3>{t("checkout.overview.details")}</h3>
      <p data-testid="overview-details">
        {`${details.name}, ${details.email}, ${details.phone}`}
        <br />
        {`${details.street}, ${details.postalCode} ${details.city}, ${details.country}`}
      </p>
      <Button
        variant="secondary"
        onClick={() => {
          goTo(1);
        }}
      >
        {`${t("checkout.edit")}: ${t("checkout.step.1")}`}
      </Button>

      <h3>{t("checkout.overview.items")}</h3>
      <table className="md-cart-table">
        <thead>
          <tr>
            <th scope="col">{t("cart.col.product")}</th>
            <th scope="col">{t("cart.col.quantity")}</th>
            <th scope="col">{t("cart.col.unitPrice")}</th>
            <th scope="col">{t("cart.col.lineNet")}</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((line) => (
            <tr key={line.productId} data-testid="overview-line">
              <th scope="row">{line.name}</th>
              <td>{line.quantity}</td>
              <td>{line.unitPrice ? format.money(line.unitPrice) : null}</td>
              <td>{line.lineNet ? format.money(line.lineNet) : null}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <Link to="/cart">{t("checkout.editCart")}</Link>

      <h3>{t("checkout.overview.delivery")}</h3>
      <p data-testid="overview-delivery">{format.money(delivery.cost)}</p>
      <Button
        variant="secondary"
        onClick={() => {
          goTo(2);
        }}
      >
        {`${t("checkout.edit")}: ${t("checkout.step.2")}`}
      </Button>

      {paymentMethod ? (
        <>
          <h3>{t("checkout.overview.method")}</h3>
          <p data-testid="overview-method">{t(`checkout.method.${paymentMethod}`)}</p>
          <Button
            variant="secondary"
            onClick={() => {
              goTo(3);
            }}
          >
            {`${t("checkout.edit")}: ${t("checkout.step.3")}`}
          </Button>
        </>
      ) : null}

      <h3>{t("checkout.overview.totals")}</h3>
      <dl>
        <dt>{t("totals.net")}</dt>
        <dd data-testid="net">{format.money(totals.net)}</dd>
        <dt>{t("totals.tax")}</dt>
        <dd data-testid="tax">{format.money(totals.tax)}</dd>
        <dt>{t("totals.delivery")}</dt>
        <dd data-testid="delivery">{format.money(totals.delivery)}</dd>
        <dt>{t("totals.grandTotal")}</dt>
        <dd data-testid="grand-total">{format.money(totals.grandTotal)}</dd>
      </dl>

      {execute.isError ? (
        <ErrorState
          error={execute.error}
          action={
            <Button
              variant="secondary"
              onClick={() => {
                void overview.refetch();
                execute.reset();
              }}
            >
              {t("checkout.error.retry")}
            </Button>
          }
        />
      ) : null}
      <Button
        disabled={execute.isPending}
        onClick={() => {
          execute.mutate(totals.grandTotal.amount, { onSuccess: setAttempt });
        }}
      >
        {invoice
          ? t("checkout.placeOrder")
          : t("checkout.pay", { amount: format.money(totals.grandTotal) })}
      </Button>
    </section>
  );
}
