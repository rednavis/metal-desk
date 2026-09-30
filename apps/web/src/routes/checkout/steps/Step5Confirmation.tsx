import { Link } from "react-router";
import type { ConfirmationKind } from "../../../api/types";
import { usePreferences } from "../../../preferences/usePreferences";
import { assertNever } from "../outcome";
import type { CheckoutApi } from "../useCheckoutSession";
import { HandoffOutcome } from "../HandoffOutcome";
import { Spinner } from "../../../ui";

/**
 * Step 5 (BRD FR-8.1): the order number and what happens next. A captured payment, an issued invoice
 * that awaits payment and a manager's quote are three different successes and read as such: none is
 * styled or worded as an error.
 */
export function Step5Confirmation({ checkout }: { checkout: CheckoutApi }) {
  const { t, format } = usePreferences();
  const confirmation = checkout.confirmation.data;
  if (confirmation === undefined) return <Spinner label={t("checkout.loading")} />;

  if (confirmation.kind === "MANAGER_QUOTE") {
    return <HandoffOutcome reference={confirmation.orderNumber} />;
  }
  return (
    <section aria-labelledby="step5-title" data-kind={confirmation.kind}>
      <h2 id="step5-title">{t("checkout.step5.title")}</h2>
      <p>
        <strong>{t(headline(confirmation.kind))}</strong>
      </p>
      <p data-testid="order-number">
        {t("checkout.confirmation.orderNumber", { number: confirmation.orderNumber })}
      </p>
      <p>{t("checkout.confirmation.status", { status: confirmation.statusLabel })}</p>
      {confirmation.total ? (
        <p>{t("checkout.confirmation.total", { total: format.money(confirmation.total) })}</p>
      ) : null}
      {confirmation.invoiceNumber ? (
        <p data-testid="invoice-number">
          {t("checkout.confirmation.invoice", { number: confirmation.invoiceNumber })}
        </p>
      ) : null}
      <p>{confirmation.message}</p>
      <Link to="/catalog">{t("checkout.confirmation.continue")}</Link>
    </section>
  );
}

function headline(kind: Exclude<ConfirmationKind, "MANAGER_QUOTE">) {
  switch (kind) {
    case "PAID":
      return "checkout.confirmation.PAID";
    case "INVOICE":
      return "checkout.confirmation.INVOICE";
    default:
      return assertNever(kind);
  }
}
