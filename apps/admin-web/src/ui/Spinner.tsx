interface SpinnerProps {
  /** What is loading, read out by screen readers. */
  label?: string;
}

/** A loading indicator that announces itself politely. */
export function Spinner({ label = "Loading" }: SpinnerProps) {
  return (
    <span role="status" aria-live="polite">
      <span className="md-spinner" aria-hidden="true" />
      <span className="md-sr-only">{label}</span>
    </span>
  );
}
