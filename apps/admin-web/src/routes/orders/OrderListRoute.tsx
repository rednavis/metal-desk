import { Link, useSearchParams } from "react-router";
import { orderStatusSchema } from "../../api/types";
import { PAGE_SIZE, useOrderPage } from "../../features/orders/useOrders";
import { amount, STATUS_LABEL } from "../../features/orders/labels";
import { EmptyState, ErrorState, Pagination, SelectField, Spinner } from "../../ui";

const ALL = "ALL";

/** Orders, newest first, a page at a time and filterable by status; the filter and page are in the address. */
export function OrderListRoute() {
  const [params, setParams] = useSearchParams();
  const parsed = orderStatusSchema.safeParse(params.get("status"));
  const status = parsed.success ? parsed.data : undefined;
  const requested = Number(params.get("page"));
  const page = Number.isInteger(requested) && requested >= 0 ? requested : 0;
  const orders = useOrderPage(status, page);

  const change = (next: { status?: string; page?: number }) => {
    const search = new URLSearchParams(params);
    if (next.status !== undefined) {
      if (next.status === ALL) search.delete("status");
      else search.set("status", next.status);
      search.delete("page");
    }
    if (next.page !== undefined) search.set("page", String(next.page));
    setParams(search);
  };

  let body;
  if (orders.isPending) body = <Spinner />;
  else if (orders.isError) body = <ErrorState error={orders.error} />;
  else if (orders.data.items.length === 0) {
    body = <EmptyState title="No orders" description="No order matches this filter." />;
  } else {
    body = (
      <>
        <table className="md-table">
          <thead>
            <tr>
              <th scope="col">Order</th>
              <th scope="col">Status</th>
              <th scope="col">Items</th>
              <th scope="col">Total</th>
              <th scope="col">Created</th>
            </tr>
          </thead>
          <tbody>
            {orders.data.items.map((order) => (
              <tr key={order.id} data-testid="order-row">
                <th scope="row">
                  <Link to={`/orders/${encodeURIComponent(order.id)}`}>{order.number}</Link>
                </th>
                <td>
                  <span className="md-status" data-status={order.status}>
                    {STATUS_LABEL[order.status]}
                  </span>
                </td>
                <td>{order.itemCount}</td>
                <td>{amount(order.total, order.currency)}</td>
                <td>{order.createdAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination
          page={orders.data.page}
          size={PAGE_SIZE}
          total={orders.data.total}
          onPage={(target) => {
            change({ page: target });
          }}
        />
      </>
    );
  }
  return (
    <>
      <h1>Orders</h1>
      <SelectField
        label="Status"
        value={status ?? ALL}
        options={[
          { value: ALL, label: "All statuses" },
          ...orderStatusSchema.options.map((value) => ({ value, label: STATUS_LABEL[value] })),
        ]}
        onChange={(value) => {
          change({ status: value });
        }}
      />
      {body}
    </>
  );
}
