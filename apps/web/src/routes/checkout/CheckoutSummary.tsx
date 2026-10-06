import type { PaymentMethod, SessionView } from "../../api/types";
import { usePreferences } from "../../preferences/usePreferences";

interface CheckoutSummaryProps {
  session: SessionView;
  method?: PaymentMethod;
}

/**
 * What the customer has put into the checkout so far: the basket, their details, the delivery quote
 * and the chosen method. It is shown on the payment-method step so that a decline visibly keeps all
 * of it (BRD FR-6.3); every part comes from the server's session.
 */
export function CheckoutSummary({ session, method }: CheckoutSummaryProps) {
  const { t, format } = usePreferences();
  const { basket, details, delivery } = session;
  return (
    <aside className="md-summary" aria-labelledby="summary-title" data-testid="checkout-summary">
      <h2 id="summary-title">{t("checkout.summary.title")}</h2>
      <h3>{t("checkout.summary.items")}</h3>
      <ul className="md-summary__lines">
        {basket.lines.map((line) => (
          <li key={line.productId} data-testid="summary-line">
            <span>{line.name}</span>
            <span className="md-summary__qty">{` × ${String(line.quantity)}`}</span>
          </li>
        ))}
      </ul>
      {details ? (
        <div className="md-summary__block">
          <h3>{t("checkout.summary.details")}</h3>
          <p data-testid="summary-details">
            {`${details.name}, ${details.email}`}
            <br />
            {`${details.street}, ${details.postalCode} ${details.city}, ${details.country}`}
          </p>
        </div>
      ) : null}
      {delivery?.quote ? (
        <div className="md-summary__block">
          <h3>{t("checkout.summary.delivery")}</h3>
          <p data-testid="summary-delivery">{format.money(delivery.quote.cost)}</p>
        </div>
      ) : null}
      {method ? (
        <div className="md-summary__block">
          <h3>{t("checkout.summary.method")}</h3>
          <p data-testid="summary-method">{t(`checkout.method.${method}`)}</p>
        </div>
      ) : null}
    </aside>
  );
}
