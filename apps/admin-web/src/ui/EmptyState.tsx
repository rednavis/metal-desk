import type { ReactNode } from "react";

interface EmptyStateProps {
  title: string;
  /** What to do next, if there is something. */
  description?: string;
  /** A call to action, such as a link back to the catalog. */
  action?: ReactNode;
}

/**
 * An explicit "there is nothing here" (BRD FR-3.4: an empty cart says so, it is not a blank page).
 * Use it for any list that can be empty, so emptiness is a designed state and not an accident.
 */
export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <section className="md-empty-state">
      <h2>{title}</h2>
      {description ? <p>{description}</p> : null}
      {action}
    </section>
  );
}
