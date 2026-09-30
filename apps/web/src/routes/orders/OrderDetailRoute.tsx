import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router";
import { orderDetailViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { ErrorState, Spinner } from "../../ui";
import { OrderStatusBadge } from "./OrderStatusBadge";

/**
 * One order in full (BRD FR-10.1): its lines and the totals stored with it, the delivery address and
 * payment method, and, only once it has shipped, the carrier and tracking number. The shipment block
 * is absent for an order that has none, never shown with empty values.
 */
export function OrderDetailRoute() {
  const { t, format } = usePreferences();
  const client = useApi();
  const { number = "" } = useParams();
  const order = useQuery({
    queryKey: ["orders", number],
    queryFn: () =>
      client.get(`/orders/${encodeURIComponent(number)}`, { schema: orderDetailViewSchema }),
  });

  if (order.isPending) return <Spinner label={t("orders.loading")} />;
  if (order.isError) return <ErrorState error={order.error} />;
  const { data } = order;
  return (
    <>
      <p>
        <Link to="/orders">{t("orders.detail.back")}</Link>
      </p>
      <h1>{t("orders.detail.title", { number: data.orderNumber })}</h1>
      <p>
        <OrderStatusBadge status={data.status} statusLabel={data.statusLabel} />
      </p>
      <p>{t("orders.detail.placed", { date: format.dateTime(data.createdAt) })}</p>

      <h2>{t("orders.detail.lines")}</h2>
      <table className="md-cart-table">
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

      <dl>
        <dt>{t("totals.net")}</dt>
        <dd data-testid="net">{format.money(data.totals.net)}</dd>
        <dt>{t("totals.tax")}</dt>
        <dd data-testid="tax">{format.money(data.totals.tax)}</dd>
        <dt>{t("totals.delivery")}</dt>
        <dd data-testid="delivery">{format.money(data.totals.delivery)}</dd>
        <dt>{t("totals.grandTotal")}</dt>
        <dd data-testid="grand-total">{format.money(data.totals.grandTotal)}</dd>
      </dl>

      {data.deliveryAddress ? (
        <>
          <h2>{t("orders.detail.address")}</h2>
          <p>
            {`${data.deliveryAddress.street}, ${data.deliveryAddress.postalCode} ${data.deliveryAddress.city}, ${data.deliveryAddress.country}`}
          </p>
        </>
      ) : null}
      {data.paymentMethod ? (
        <>
          <h2>{t("orders.detail.method")}</h2>
          <p>{data.paymentMethod}</p>
        </>
      ) : null}
      {data.shipment ? (
        <section aria-labelledby="shipment-title" data-testid="shipment">
          <h2 id="shipment-title">{t("orders.detail.shipment")}</h2>
          <dl>
            <dt>{t("orders.detail.carrier")}</dt>
            <dd>{data.shipment.carrier}</dd>
            <dt>{t("orders.detail.tracking")}</dt>
            <dd>{data.shipment.trackingReference}</dd>
          </dl>
        </section>
      ) : null}
    </>
  );
}
