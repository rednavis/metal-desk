import { useState, type FormEvent } from "react";
import { isApiError } from "../../api/errors";
import { Button, ErrorState, Field } from "../../ui";

interface ShipmentFormProps {
  busy: boolean;
  error: unknown;
  onSubmit: (entry: { carrier: string; trackingReference: string }) => void;
}

/**
 * Carrier and tracking entry. Both are required together: the customer's order history shows both
 * (BRD FR-10.1), so one without the other is half a shipment and is not sent. The parent renders this
 * only where the server accepts the shipped trigger.
 */
export function ShipmentForm({ busy, error, onSubmit }: ShipmentFormProps) {
  const [carrier, setCarrier] = useState("");
  const [tracking, setTracking] = useState("");
  const [missing, setMissing] = useState<{ carrier?: string; tracking?: string }>({});

  function submit(event: FormEvent) {
    event.preventDefault();
    const gaps = {
      carrier: carrier.trim() ? undefined : "Enter the carrier",
      tracking: tracking.trim() ? undefined : "Enter the tracking reference",
    };
    setMissing(gaps);
    if (gaps.carrier || gaps.tracking) return;
    onSubmit({ carrier: carrier.trim(), trackingReference: tracking.trim() });
  }

  return (
    <form className="md-form" onSubmit={submit} noValidate aria-label="Shipment">
      {error ? (
        isApiError(error) ? (
          <p role="alert">{error.message}</p>
        ) : (
          <ErrorState error={error} />
        )
      ) : null}
      <Field
        label="Carrier"
        name="carrier"
        value={carrier}
        error={missing.carrier}
        onChange={(event) => {
          setCarrier(event.target.value);
        }}
      />
      <Field
        label="Tracking reference"
        name="tracking"
        value={tracking}
        error={missing.tracking}
        onChange={(event) => {
          setTracking(event.target.value);
        }}
      />
      <Button type="submit" disabled={busy}>
        Mark as shipped
      </Button>
    </form>
  );
}
