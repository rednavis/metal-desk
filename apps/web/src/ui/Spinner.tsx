import { usePreferences } from "../preferences/usePreferences";

interface SpinnerProps {
  /** What is loading, read out by screen readers; "Loading" in the active language by default. */
  label?: string;
}

/** A loading indicator that announces itself politely. */
export function Spinner({ label }: SpinnerProps) {
  const { t } = usePreferences();
  return (
    <span role="status" aria-live="polite">
      <span className="md-spinner" aria-hidden="true" />
      <span className="md-sr-only">{label ?? t("spinner.loading")}</span>
    </span>
  );
}
