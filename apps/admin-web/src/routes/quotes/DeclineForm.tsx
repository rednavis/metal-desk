import { useState, type FormEvent } from "react";
import type { QuoteOutcome } from "../../api/types";
import { Button, ConfirmDialog, ErrorState, TextAreaField } from "../../ui";

const REASON_MAX = 500;

interface DeclineFormProps {
  number: string;
  busy: boolean;
  error: unknown;
  outcome: QuoteOutcome | undefined;
  onSubmit: (reason: string) => void;
}

/**
 * Declining a quote. A reason is required and the decision is confirmed in a dialog that says what
 * it does: the customer's order is cancelled. The reason goes to the server's audit log beside who
 * declined; it is not yet mailed to the customer.
 */
export function DeclineForm({ number, busy, error, outcome, onSubmit }: DeclineFormProps) {
  const [reason, setReason] = useState("");
  const [problem, setProblem] = useState<string | undefined>();
  const [confirming, setConfirming] = useState(false);

  if (outcome) {
    return (
      <section role="status" className="md-notice md-notice--ok" data-testid="decline-outcome">
        <strong>{`Quote declined. Order ${outcome.number} is now ${outcome.status}.`}</strong>
      </section>
    );
  }

  function review(event: FormEvent) {
    event.preventDefault();
    if (!reason.trim()) return setProblem("Give a reason for declining");
    if (reason.trim().length > REASON_MAX) {
      return setProblem(`The reason must be at most ${String(REASON_MAX)} characters`);
    }
    setProblem(undefined);
    setConfirming(true);
  }

  return (
    <section aria-labelledby="decline-title">
      <h2 id="decline-title">Decline the quote</h2>
      <form className="md-form" onSubmit={review} noValidate>
        {error ? <ErrorState error={error} /> : null}
        <TextAreaField label="Reason" value={reason} error={problem} onChange={setReason} />
        <Button type="submit" variant="secondary" disabled={busy}>
          Decline the quote
        </Button>
      </form>
      {confirming ? (
        <ConfirmDialog
          title="Decline this quote?"
          confirmLabel="Decline and cancel the order"
          busy={busy}
          onCancel={() => {
            setConfirming(false);
          }}
          onConfirm={() => {
            onSubmit(reason.trim());
            setConfirming(false);
          }}
        >
          <p>{`Order ${number} will be cancelled. The customer will not be able to pay for it.`}</p>
        </ConfirmDialog>
      ) : null}
    </section>
  );
}
