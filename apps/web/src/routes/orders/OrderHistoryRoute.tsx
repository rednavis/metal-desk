import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { orderHistoryViewSchema } from "../../api/types";
import { useApi } from "../../api/useApi";
import { usePreferences } from "../../preferences/usePreferences";
import { EmptyState, ErrorState, Spinner } from "../../ui";
import { OrderStatusBadge } from "./OrderStatusBadge";

/**
 * The signed-in customer's orders (BRD FR-10.1): number, date, item count, total and status, each
 * linking to its detail. Totals are the amounts stored with the order and are shown as they arrive;
 * nothing is recalculated from today's prices.
 */
export function OrderHistoryRoute() {
  const { t, format } = usePreferences();
  const client = useApi();
  const history = useQuery({
    queryKey: ["orders"],
    queryFn: () => client.get("/orders", { schema: orderHistoryViewSchema }),
  });

  let body;
  if (history.isPending) body = <Spinner label={t("orders.loading")} />;
  else if (history.isError) body = <ErrorState error={history.error} />;
  else if (history.data.orders.length === 0) {
    body = (
      <EmptyState
        title={t("orders.empty.title")}
        description={t("orders.empty.description")}
        action={<Link to="/catalog">{t("page.cart.empty.browse")}</Link>}
      />
    );
  } else {
    body = (
      <table className="md-table">
        <thead>
          <tr>
            <th scope="col">{t("orders.col.number")}</th>
            <th scope="col">{t("orders.col.date")}</th>
            <th scope="col">{t("orders.col.items")}</th>
            <th scope="col">{t("orders.col.total")}</th>
            <th scope="col">{t("orders.col.status")}</th>
          </tr>
        </thead>
        <tbody>
          {history.data.orders.map((order) => (
            <tr key={order.orderNumber} data-testid="order-row">
              <th scope="row">
                <Link to={`/orders/${encodeURIComponent(order.orderNumber)}`}>
                  {order.orderNumber}
                </Link>
              </th>
              <td>{format.dateTime(order.createdAt)}</td>
              <td>{order.itemCount}</td>
              <td data-testid="order-total">{format.money(order.total)}</td>
              <td>
                <OrderStatusBadge status={order.status} statusLabel={order.statusLabel} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    );
  }
  return (
    <>
      <h1>{t("orders.title")}</h1>
      {body}
    </>
  );
}
