import { useId, type ReactNode } from "react";

interface ChoiceProps {
  label: string;
  error?: string;
  children: (props: {
    id: string;
    "aria-invalid"?: true;
    "aria-describedby"?: string;
  }) => ReactNode;
}

function Labelled({ label, error, children }: ChoiceProps) {
  const id = useId();
  const errorId = `${id}-error`;
  return (
    <div className="md-field">
      <label htmlFor={id}>{label}</label>
      {children({
        id,
        "aria-invalid": error ? true : undefined,
        "aria-describedby": error ? errorId : undefined,
      })}
      {error ? (
        <span id={errorId} className="md-field__error">
          {error}
        </span>
      ) : null}
    </div>
  );
}

interface SelectFieldProps {
  label: string;
  value: string;
  options: readonly { value: string; label: string }[];
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
}

/** A labelled drop-down with its error message, wired together for screen readers. */
export function SelectField({
  label,
  value,
  options,
  onChange,
  error,
  disabled,
}: SelectFieldProps) {
  return (
    <Labelled label={label} error={error}>
      {(aria) => (
        <select
          {...aria}
          value={value}
          disabled={disabled}
          onChange={(event) => {
            onChange(event.target.value);
          }}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}
    </Labelled>
  );
}

interface TextAreaFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  rows?: number;
}

/** A labelled multi-line input with its error message, wired together for screen readers. */
export function TextAreaField({ label, value, onChange, error, rows = 4 }: TextAreaFieldProps) {
  return (
    <Labelled label={label} error={error}>
      {(aria) => (
        <textarea
          {...aria}
          rows={rows}
          value={value}
          onChange={(event) => {
            onChange(event.target.value);
          }}
        />
      )}
    </Labelled>
  );
}
