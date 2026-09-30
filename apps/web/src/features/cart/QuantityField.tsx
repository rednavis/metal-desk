import { useState, type FormEvent } from "react";
import { usePreferences } from "../../preferences/usePreferences";
import { Button, Field } from "../../ui";

interface QuantityFieldProps {
  /** The product's name, for the accessible label. */
  name: string;
  quantity: number;
  /** The per-line cap the server reported. */
  max: number;
  /** The server's refusal of the last change to this line, if any. */
  serverError?: string;
  disabled?: boolean;
  onChange: (quantity: number) => void;
}

/**
 * A line's quantity. The cap is enforced before sending (the input refuses above it, and an
 * over-cap entry is reported and not sent) and the server's own refusal is shown if it still
 * arrives: the field never clamps, so what the customer typed stays in the box beside the
 * explanation, instead of the cart silently holding another number.
 */
export function QuantityField({
  name,
  quantity,
  max,
  serverError,
  disabled,
  onChange,
}: QuantityFieldProps) {
  const { t } = usePreferences();
  const [draft, setDraft] = useState<string | undefined>();
  const [invalid, setInvalid] = useState(false);
  const text = draft ?? String(quantity);

  function submit(event: FormEvent) {
    event.preventDefault();
    const value = Number(text);
    if (!Number.isInteger(value) || value < 1 || value > max) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    if (value !== quantity) onChange(value);
    setDraft(undefined);
  }

  const error = invalid
    ? t(Number(text) > max ? "cart.quantity.max" : "cart.quantity.invalid", { max })
    : serverError;

  return (
    <form className="md-quantity" onSubmit={submit} noValidate>
      <Field
        label={t("cart.quantity.label", { name })}
        type="number"
        inputMode="numeric"
        min={1}
        max={max}
        value={text}
        error={error}
        disabled={disabled}
        onChange={(event) => {
          setDraft(event.target.value);
          setInvalid(false);
        }}
      />
      <Button type="submit" variant="secondary" disabled={disabled || draft === undefined}>
        {t("cart.quantity.update")}
      </Button>
    </form>
  );
}
