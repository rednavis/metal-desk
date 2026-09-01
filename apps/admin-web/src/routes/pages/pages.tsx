import type { ReactNode } from "react";
import { Link } from "react-router";
import { useQuotePage } from "../../features/orders/useOrders";
import { EmptyState } from "../../ui";

/** One tile of the overview: a section of the back office, with a count when one is known. */
function SectionTile({
  to,
  label,
  value,
  hint,
}: {
  to: string;
  label: string;
  value?: ReactNode;
  hint: string;
}) {
  return (
    <li>
      <Link to={to} className="md-stat">
        <span className="md-stat__label">{label}</span>
        {value === undefined ? null : <span className="md-stat__value">{value}</span>}
        <span className="md-stat__hint">{hint}</span>
      </Link>
    </li>
  );
}

/** The landing page: what is waiting for a person, and the way into each section. */
export function OverviewPage() {
  const quotes = useQuotePage(0);
  return (
    <>
      <h1>MetalDesk Admin</h1>
      <p>Delivery tiers, manager quotes and orders are managed from here.</p>
      <ul className="md-stats">
        <SectionTile
          to="/quotes"
          label="Manager quotes"
          value={quotes.data ? quotes.data.total : undefined}
          hint="Orders handed to a manager, waiting for a price."
        />
        <SectionTile to="/orders" label="Orders" hint="Every order, filterable by status." />
        <SectionTile
          to="/tiers"
          label="Delivery tiers"
          hint="Delivery prices and ceilings per region."
        />
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
