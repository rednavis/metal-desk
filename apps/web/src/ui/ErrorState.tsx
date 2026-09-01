import type { ReactNode } from "react";
import { isApiError } from "../api/errors";
import { usePreferences } from "../preferences/usePreferences";

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
 *
 * The server's own `message` is shown as it arrives, in English: the API does not localise its
 * error text, which is a known limitation; the heading, the reference sentence and the fallback
 * are translated.
 */
export function ErrorState({ error, action }: ErrorStateProps) {
  const { t } = usePreferences();
  return (
    <section role="alert" className="md-error-state">
      <h2>{t("error.title")}</h2>
      {isApiError(error) ? (
        <>
          <p>{error.message}</p>
          <p>{t("error.reference", { reference: error.correlationId, code: error.code })}</p>
        </>
      ) : (
        <p>{t("error.unexpected")}</p>
      )}
      {action}
    </section>
  );
}
