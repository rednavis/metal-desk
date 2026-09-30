import { Link } from "react-router";
import { useQuotePage } from "../../features/orders/useOrders";
import { EmptyState } from "../../ui";

/** The landing page: what is waiting for a person. */
export function OverviewPage() {
  const quotes = useQuotePage(0);
  return (
    <>
      <h1>MetalDesk Admin</h1>
      <p>
        {quotes.data
          ? `Manager quotes waiting: ${String(quotes.data.total)}.`
          : "Delivery tiers, manager quotes and orders are managed from here."}
      </p>
      <ul>
        <li>
          <Link to="/quotes">Manager quotes</Link>
        </li>
        <li>
          <Link to="/orders">Orders</Link>
        </li>
        <li>
          <Link to="/tiers">Delivery tiers</Link>
        </li>
      </ul>
    </>
  );
}

export function NotFoundPage() {
  return (
    <EmptyState
      title="Page not found"
      description="The page you asked for does not exist."
      action={<Link to="/">Go to the overview</Link>}
    />
  );
}
