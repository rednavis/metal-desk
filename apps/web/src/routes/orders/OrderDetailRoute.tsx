import { useMutation, useQuery } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router";
import {
  orderDetailViewSchema,
  paymentMethodSchema,
  resumedPaymentViewSchema,
} from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { AmountSummary, Button, ErrorState, Spinner } from "../../ui";
import { OrderStatusBadge } from "./OrderStatusBadge";

/**
 * One order in full (BRD FR-10.1): its lines and the totals stored with it, the delivery address and
 * payment method, and, only once it has shipped, the carrier and tracking number. The shipment block
 * is absent for an order that has none, never shown with empty values.
 *
 * The amounts and the delivery address sit in a summary beside the items. An order that is awaiting
 * payment offers "Pay now": the server opens a checkout around this very order (so it is charged
 * once, at the total it carries) and the customer continues at the choice of payment method.
 */
export function OrderDetailRoute() {
  const { t, format } = usePreferences();
  const client = useApi();
  const { number = "" } = useParams();
  const navigate = useNavigate();
  const order = useQuery({
    queryKey: ["orders", number],
    queryFn: () =>
      client.get(`/orders/${encodeURIComponent(number)}`, { schema: orderDetailViewSchema }),
  });

  const pay = useMutation({
    mutationFn: () =>
      client.post(`/orders/${encodeURIComponent(number)}/payment-session`, {
        schema: resumedPaymentViewSchema,
      }),
    onSuccess: (view) => {
      void navigate({ pathname: `/checkout/${view.checkoutId}`, search: "?step=3" });
    },
  });

  if (order.isPending) return <Spinner label={t("orders.loading")} />;
  if (order.isError) return <ErrorState error={order.error} />;
  const { data } = order;
  // An invoice was issued and awaits the bank transfer: paying again would issue a second one.
  const method = paymentMethodSchema.safeParse(data.paymentMethod);
  const methodLabel = method.success ? t(`checkout.method.${method.data}`) : data.paymentMethod;
  const invoiceIssued = data.paymentMethod === "INVOICE" && data.paymentStatus === "PENDING";
  return (
    <>
      <p>
        <Link to="/orders">{t("orders.detail.back")}</Link>
      </p>
      <header className="md-order-header">
        <h1>{t("orders.detail.title", { number: data.orderNumber })}</h1>
        <dl className="md-order-meta">
          <div>
            <dt>{t("orders.detail.statusLabel")}</dt>
            <dd>
              <OrderStatusBadge status={data.status} statusLabel={data.statusLabel} />
            </dd>
          </div>
          <div>
            <dt>{t("orders.detail.placedLabel")}</dt>
            <dd>
              <time dateTime={data.createdAt} data-testid="placed">
                {format.dateTime(data.createdAt)}
              </time>
            </dd>
          </div>
        </dl>
      </header>

      <div className="md-split">
        <section aria-labelledby="order-items-title">
          <h2 id="order-items-title">{t("orders.detail.lines")}</h2>
          <table className="md-table">
            <thead>
              <tr>
                <th scope="col">{t("cart.col.product")}</th>
                <th scope="col">{t("cart.col.quantity")}</th>
                <th scope="col">{t("cart.col.unitPrice")}</th>
                <th scope="col">{t("cart.col.lineNet")}</th>
                <th scope="col">{t("cart.col.lineTax")}</th>
              </tr>
            </thead>
            <tbody>
              {data.lines.map((line) => (
                <tr key={line.productName} data-testid="order-line">
                  <th scope="row">{line.productName}</th>
                  <td>{line.quantity}</td>
                  <td>{format.money(line.unitPrice)}</td>
                  <td>{format.money(line.lineNet)}</td>
                  <td>{format.money(line.lineTax)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <aside className="md-summary" aria-labelledby="order-summary-title">
          <h2 id="order-summary-title">{t("checkout.overview.totals")}</h2>
          <AmountSummary totals={data.totals} />

          {data.deliveryAddress ? (
            <div className="md-summary__block">
              <h3>{t("orders.detail.address")}</h3>
              <address data-testid="delivery-address">
                {data.deliveryAddress.street}
                <br />
                {`${data.deliveryAddress.postalCode} ${data.deliveryAddress.city}`}
                <br />
                {data.deliveryAddress.country}
              </address>
            </div>
          ) : null}

          {data.status === "AWAITING_PAYMENT" && invoiceIssued ? (
            <div className="md-summary__block">
              <p data-testid="invoice-pending">{t("orders.detail.invoicePending")}</p>
            </div>
          ) : null}
          {data.status === "AWAITING_PAYMENT" && !invoiceIssued ? (
            <div className="md-summary__block">
              <p>{t("orders.detail.payHint")}</p>
              <Button
                disabled={pay.isPending}
                onClick={() => {
                  pay.mutate();
                }}
              >
                {pay.isPending ? t("orders.detail.paying") : t("orders.detail.pay")}
              </Button>
              {pay.isError ? <ErrorState error={pay.error} /> : null}
            </div>
          ) : null}
        </aside>
      </div>

      {data.paymentMethod || data.shipment ? (
        <div className="md-info-cards">
          {data.paymentMethod ? (
            <section className="md-panel" aria-labelledby="payment-title" data-testid="payment">
              <h2 id="payment-title">{t("orders.detail.method")}</h2>
              <p className="md-panel__value">{methodLabel}</p>
            </section>
          ) : null}
          {data.shipment ? (
            <section className="md-panel" aria-labelledby="shipment-title" data-testid="shipment">
              <h2 id="shipment-title">{t("orders.detail.shipment")}</h2>
              <dl className="md-panel__rows">
                <div>
                  <dt>{t("orders.detail.carrier")}</dt>
                  <dd>{data.shipment.carrier}</dd>
                </div>
                <div>
                  <dt>{t("orders.detail.tracking")}</dt>
                  <dd>{data.shipment.trackingReference}</dd>
                </div>
              </dl>
            </section>
          ) : null}
        </div>
      ) : null}
    </>
  );
}
