import { Link } from "react-router";
import { EmptyState } from "../../ui";

/** Route shells: each owns its URL and says what will live there. The screens are T-056's. */

export function OverviewPage() {
  return (
    <>
      <h1>MetalDesk Admin</h1>
      <p>Tiers, manager quotes and orders will be managed from here.</p>
    </>
  );
}

export function TiersPage() {
  return <h1>Delivery tiers</h1>;
}

export function QuotesPage() {
  return <h1>Manager quotes</h1>;
}

export function OrdersPage() {
  return <h1>Orders</h1>;
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
