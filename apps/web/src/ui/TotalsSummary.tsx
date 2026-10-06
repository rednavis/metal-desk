import type { TotalsView } from "../api/types";
import { usePreferences } from "../preferences/usePreferences";

/** Net, tax and total of a cart or an order, formatted in the active language and the currency they arrive in. */
export function TotalsSummary({ totals }: { totals: TotalsView }) {
  const { t, format } = usePreferences();
  return (
    <dl className="md-totals">
      <dt>{t("totals.net")}</dt>
      <dd data-testid="net">{format.money(totals.net)}</dd>
      <dt>{t("totals.tax")}</dt>
      <dd data-testid="tax">{format.money(totals.tax)}</dd>
      <dt>{t("totals.total")}</dt>
      <dd data-testid="total">{format.money(totals.total)}</dd>
    </dl>
  );
}
