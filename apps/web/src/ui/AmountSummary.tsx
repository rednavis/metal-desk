import type { PriceView } from "../api/types";
import { usePreferences } from "../preferences/usePreferences";

interface AmountSummaryProps {
  totals: { net: PriceView; tax: PriceView; delivery: PriceView; grandTotal: PriceView };
}

/**
 * The amounts of an order or a checkout, as a payment page shows them: the amount due, large, then
 * net, tax, delivery and the grand total. Every figure is the server's, formatted and never added
 * up here.
 */
export function AmountSummary({ totals }: AmountSummaryProps) {
  const { t, format } = usePreferences();
  return (
    <>
      <p className="md-summary__due">
        <span>{t("orders.detail.due")}</span>
        <strong>{format.money(totals.grandTotal)}</strong>
      </p>
      <dl className="md-summary__rows">
        <div>
          <dt>{t("totals.net")}</dt>
          <dd data-testid="net">{format.money(totals.net)}</dd>
        </div>
        <div>
          <dt>{t("totals.tax")}</dt>
          <dd data-testid="tax">{format.money(totals.tax)}</dd>
        </div>
        <div>
          <dt>{t("totals.delivery")}</dt>
          <dd data-testid="delivery">{format.money(totals.delivery)}</dd>
        </div>
        <div className="md-summary__total">
          <dt>{t("totals.grandTotal")}</dt>
          <dd data-testid="grand-total">{format.money(totals.grandTotal)}</dd>
        </div>
      </dl>
    </>
  );
}
