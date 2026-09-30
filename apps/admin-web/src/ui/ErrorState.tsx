import type { ReactNode } from "react";
import { isApiError } from "../api/errors";

interface ErrorStateProps {
  /** Whatever was thrown; an `ApiError` also shows its code and correlation id. */
  error: unknown;
  /** A retry button or similar. */
  action?: ReactNode;
}

/**
 * An actionable error (BRD FR-8.1): what went wrong in words, and the reference to quote to
 * support. Anything that is not an `ApiError` gets a generic message and no reference, because it
 * did not come from the server and there is nothing to look up.
 */
export function ErrorState({ error, action }: ErrorStateProps) {
  return (
    <section role="alert" className="md-error-state">
      <h2>Something went wrong</h2>
      {isApiError(error) ? (
        <>
          <p>{error.message}</p>
          <p>
            If you contact us, quote reference <code>{error.correlationId}</code> ({error.code}).
          </p>
        </>
      ) : (
        <p>An unexpected error occurred. Reload the page and try again.</p>
      )}
      {action}
    </section>
  );
}
