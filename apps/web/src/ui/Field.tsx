import { useId, type InputHTMLAttributes } from "react";

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  /** The message to show under the input; also marks it invalid for assistive technology. */
  error?: string;
  /** Keeps the label for screen readers only, for a field whose purpose its context makes plain (a search box). */
  hideLabel?: boolean;
}

/** A labelled input with its error message, wired together for screen readers. */
export function Field({ label, error, hideLabel, id, ...rest }: FieldProps) {
  const generated = useId();
  const inputId = id ?? generated;
  const errorId = `${inputId}-error`;
  return (
    <div className="md-field">
      <label htmlFor={inputId} className={hideLabel ? "md-sr-only" : undefined}>
        {label}
      </label>
      <input
        id={inputId}
        className="md-field__input"
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        {...rest}
      />
      {error ? (
        <span id={errorId} className="md-field__error">
          {error}
        </span>
      ) : null}
    </div>
  );
}
