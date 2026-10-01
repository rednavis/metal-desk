import { useState, type ReactNode } from "react";
import { Link } from "react-router";
import { usePreferences } from "../../../preferences/usePreferences";
import { AmountSummary, Button, ErrorState, Spinner } from "../../../ui";
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

  // Once an order exists (payment was started, or a manager-approved order is being paid) its
  // lines are fixed, so the way back to the cart is not offered.
  const orderExists = overview.data.orderReference !== undefined;
  const invoice = checkout.methods.data?.methods.some(
    (offered) => offered.method === paymentMethod && offered.group === "INVOICE",
  );
  return (
    <div className="md-split">
      <section aria-labelledby="step4-title">
        <h2 id="step4-title">{t("checkout.step4.title")}</h2>

        <OverviewPanel
          title={t("checkout.overview.details")}
          editLabel={`${t("checkout.edit")}: ${t("checkout.step.1")}`}
          onEdit={() => {
            goTo(1);
          }}
        >
          <dl className="md-panel__rows" data-testid="overview-details">
            <div>
              <dt>{t("checkout.field.name")}</dt>
              <dd>{details.name}</dd>
            </div>
            <div>
              <dt>{t("checkout.field.email")}</dt>
              <dd>{details.email}</dd>
            </div>
            <div>
              <dt>{t("checkout.field.phone")}</dt>
              <dd>{details.phone}</dd>
            </div>
            <div>
              <dt>{t("checkout.overview.address")}</dt>
              <dd>
                <address>
                  {details.street}
                  <br />
                  {`${details.postalCode} ${details.city}, ${details.country}`}
                </address>
              </dd>
            </div>
          </dl>
        </OverviewPanel>

        <section className="md-panel md-panel--table" aria-labelledby="step4-items-title">
          <div className="md-panel__head">
            <h3 id="step4-items-title">{t("checkout.overview.items")}</h3>
          </div>
          <table className="md-table">
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
          {orderExists ? null : (
            <p className="md-panel__foot">
              <Link to="/cart">{t("checkout.editCart")}</Link>
            </p>
          )}
        </section>

        <OverviewPanel
          title={t("checkout.overview.delivery")}
          editLabel={`${t("checkout.edit")}: ${t("checkout.step.2")}`}
          onEdit={() => {
            goTo(2);
          }}
        >
          <p className="md-panel__value" data-testid="overview-delivery">
            {format.money(delivery.cost)}
          </p>
        </OverviewPanel>

        {paymentMethod ? (
          <OverviewPanel
            title={t("checkout.overview.method")}
            editLabel={`${t("checkout.edit")}: ${t("checkout.step.3")}`}
            onEdit={() => {
              goTo(3);
            }}
          >
            <p className="md-panel__value" data-testid="overview-method">
              {t(`checkout.method.${paymentMethod}`)}
            </p>
          </OverviewPanel>
        ) : null}
      </section>

      <aside className="md-summary" aria-labelledby="step4-totals-title">
        <h2 id="step4-totals-title">{t("checkout.overview.totals")}</h2>
        <AmountSummary totals={totals} />

        <div className="md-summary__block">
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
        </div>
      </aside>
    </div>
  );
}

/** One boxed block of the overview: its title, an edit action for the step it came from, its content. */
function OverviewPanel({
  title,
  editLabel,
  onEdit,
  children,
}: {
  title: string;
  editLabel: string;
  onEdit: () => void;
  children: ReactNode;
}) {
  const { t } = usePreferences();
  return (
    <section className="md-panel">
      <div className="md-panel__head">
        <h3>{title}</h3>
        <Button
          variant="secondary"
          className="md-button--sm"
          aria-label={editLabel}
          onClick={onEdit}
        >
          {t("checkout.edit")}
        </Button>
      </div>
      {children}
    </section>
  );
}
