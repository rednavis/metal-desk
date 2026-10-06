import type { TierWarning } from "../../api/types";

/** What the server says a tier change means for checkout, verbatim and prominent (`T-040`'s warnings). */
export function WarningList({ warnings }: { warnings: readonly TierWarning[] }) {
  if (warnings.length === 0) return null;
  return (
    <section role="status" className="md-notice md-notice--warning" data-testid="tier-warnings">
      <strong>Check what this means for checkout:</strong>
      <ul>
        {warnings.map((warning) => (
          <li key={`${warning.code}-${warning.message}`} data-code={warning.code}>
            {warning.message}
          </li>
        ))}
      </ul>
    </section>
  );
}
