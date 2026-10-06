import { useEffect, useEffectEvent, useState, type FormEvent } from "react";
import { usePreferences } from "../../preferences/usePreferences";
import { Field } from "../../ui";

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

/** How long the customer may pause while typing before the new quantity is sent. */
const SETTLE_MS = 600;

/**
 * A line's quantity. A change is sent by itself once the customer stops typing, and at once on
 * Enter or when the field loses focus, so there is nothing to press to recalculate the cart.
 *
 * The cap is enforced before sending (an over-cap or non-whole entry is reported and not sent) and
 * the server's own refusal is shown if it still arrives: the field never clamps while the
 * customer types, so what they typed stays in the box beside the explanation. While a change is
 * being applied the field is read-only rather than disabled, so the focus stays where it was.
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

  function commit(entered: string) {
    const value = Number(entered);
    if (entered.trim() === "" || !Number.isInteger(value) || value < 1 || value > max) {
      setInvalid(true);
      return;
    }
    setInvalid(false);
    if (value !== quantity) onChange(value);
    setDraft(undefined);
  }

  // The timer fires later than the render that set it, so it reads the latest props and state
  // through an effect event rather than the ones it was created with.
  const settle = useEffectEvent((entered: string) => {
    commit(entered);
  });

  useEffect(() => {
    if (draft === undefined) return undefined;
    const timer = setTimeout(() => {
      settle(draft);
    }, SETTLE_MS);
    return () => {
      clearTimeout(timer);
    };
  }, [draft]);

  const error = invalid
    ? t(Number(text) > max ? "cart.quantity.max" : "cart.quantity.invalid", { max })
    : serverError;

  return (
    <form
      className="md-quantity"
      onSubmit={(event: FormEvent) => {
        event.preventDefault();
        commit(text);
      }}
      noValidate
    >
      <Field
        label={t("cart.quantity.label", { name })}
        type="number"
        inputMode="numeric"
        min={1}
        max={max}
        value={text}
        error={error}
        readOnly={disabled}
        aria-busy={disabled ? true : undefined}
        onChange={(event) => {
          setDraft(event.target.value);
          setInvalid(false);
        }}
        onBlur={() => {
          if (draft !== undefined) commit(draft);
        }}
      />
    </form>
  );
}
