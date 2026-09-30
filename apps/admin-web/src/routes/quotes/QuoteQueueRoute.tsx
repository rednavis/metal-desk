import { Link, useSearchParams } from "react-router";
import { amount } from "../../features/orders/labels";
import { PAGE_SIZE, useQuotePage } from "../../features/orders/useOrders";
import { EmptyState, ErrorState, Pagination, Spinner } from "../../ui";

/**
 * The handoff queue (BRD FR-5.3): the orders a customer handed to a manager, oldest decisions first
 * being the newest at the top, a page at a time. Every order here is in one status by definition (it
 * awaits a quote), so there is nothing to filter by; the order list has the status filter.
 */
export function QuoteQueueRoute() {
  const [params, setParams] = useSearchParams();
  const requested = Number(params.get("page"));
  const page = Number.isInteger(requested) && requested >= 0 ? requested : 0;
  const quotes = useQuotePage(page);

  let body;
  if (quotes.isPending) body = <Spinner />;
  else if (quotes.isError) body = <ErrorState error={quotes.error} />;
  else if (quotes.data.items.length === 0) {
    body = (
      <EmptyState
        title="No orders are waiting for a quote"
        description="Orders over a delivery ceiling appear here when a customer hands them to a manager."
      />
    );
  } else {
    body = (
      <>
        <table className="md-table">
          <thead>
            <tr>
              <th scope="col">Order</th>
              <th scope="col">Items</th>
              <th scope="col">Goods total</th>
              <th scope="col">Handed over</th>
            </tr>
          </thead>
          <tbody>
            {quotes.data.items.map((order) => (
              <tr key={order.id} data-testid="quote-row">
                <th scope="row">
                  <Link to={`/quotes/${encodeURIComponent(order.id)}`}>{order.number}</Link>
                </th>
                <td>{order.itemCount}</td>
                <td>{amount(order.total, order.currency)}</td>
                <td>{order.updatedAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination
          page={quotes.data.page}
          size={PAGE_SIZE}
          total={quotes.data.total}
          onPage={(target) => {
            setParams({ page: String(target) });
          }}
        />
      </>
    );
  }
  return (
    <>
      <h1>Manager quotes</h1>
      {body}
    </>
  );
}
