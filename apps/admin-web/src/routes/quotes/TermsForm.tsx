import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { addDecimals, isPositiveDecimal } from "../../api/decimal";
import type { ManagerQuoteRequest, OrderDetailView, QuoteOutcome } from "../../api/types";
import { amount } from "../../features/orders/labels";
import { Button, ConfirmDialog, ErrorState, Field, TextAreaField } from "../../ui";

interface TermsFormProps {
  order: OrderDetailView;
  busy: boolean;
  error: unknown;
  /** The server's answer once the terms are set. */
  outcome: QuoteOutcome | undefined;
  onSubmit: (request: ManagerQuoteRequest) => void;
}

interface Problems {
  price?: string;
  terms?: string;
  days?: string;
  validUntil?: string;
}

/**
 * Setting the final delivery price and terms (BRD FR-5.3). What the customer will be asked to pay is
 * shown as the price is typed, added up exactly from the order's own net and tax, because a price set
 * without seeing the total is set blind; sending goes through a confirmation that names that total
 * and what happens next. Once the server has answered, the figure it reports is what is shown.
 */
export function TermsForm({ order, busy, error, outcome, onSubmit }: TermsFormProps) {
  const currency = order.summary.currency;
  const [price, setPrice] = useState("");
  const [terms, setTerms] = useState("");
  const [minDays, setMinDays] = useState("");
  const [maxDays, setMaxDays] = useState("");
  const [validUntil, setValidUntil] = useState("");
  const [problems, setProblems] = useState<Problems>({});
  const [request, setRequest] = useState<ManagerQuoteRequest | undefined>();

  if (outcome) {
    return (
      <section role="status" className="md-notice md-notice--ok" data-testid="terms-outcome">
        <strong>{`Terms sent. Order ${outcome.number} is now ${outcome.status}.`}</strong>
        <p data-testid="outcome-total">
          {`The customer will be asked to pay ${outcome.total ?? ""} ${outcome.currency ?? currency}.`}
        </p>
        <Link to="/quotes">Back to the quotes</Link>
      </section>
    );
  }

  const preview = isPositiveDecimal(price.trim())
    ? addDecimals(order.net, order.tax, price.trim())
    : undefined;

  function review(event: FormEvent) {
    event.preventDefault();
    const min = Number(minDays);
    const max = Number(maxDays);
    const until = new Date(validUntil);
    const found: Problems = {
      price: isPositiveDecimal(price.trim()) ? undefined : "Enter a delivery price above zero",
      terms: terms.trim() ? undefined : "Write the terms the customer is being offered",
      days:
        Number.isInteger(min) && Number.isInteger(max) && min >= 1 && max >= min
          ? undefined
          : "Enter whole days: at least 1, and the most no smaller than the fewest",
      validUntil:
        validUntil && !Number.isNaN(until.getTime()) && until.getTime() > Date.now()
          ? undefined
          : "Choose a date in the future",
    };
    setProblems(found);
    if (Object.values(found).some(Boolean)) return;
    setRequest({
      deliveryPrice: price.trim(),
      terms: terms.trim(),
      transitMinDays: min,
      transitMaxDays: max,
      validUntil: until.toISOString(),
    });
  }

  return (
    <section aria-labelledby="terms-title">
      <h2 id="terms-title">Set the terms</h2>
      <form className="md-form" onSubmit={review} noValidate>
        {error ? <ErrorState error={error} /> : null}
        <Field
          label={`Delivery price (${currency})`}
          name="deliveryPrice"
          inputMode="decimal"
          value={price}
          error={problems.price}
          onChange={(event) => {
            setPrice(event.target.value);
          }}
        />
        <TextAreaField label="Terms" value={terms} error={problems.terms} onChange={setTerms} />
        <Field
          label="Fewest days in transit"
          name="transitMinDays"
          inputMode="numeric"
          value={minDays}
          error={problems.days}
          onChange={(event) => {
            setMinDays(event.target.value);
          }}
        />
        <Field
          label="Most days in transit"
          name="transitMaxDays"
          inputMode="numeric"
          value={maxDays}
          onChange={(event) => {
            setMaxDays(event.target.value);
          }}
        />
        <Field
          label="Offer valid until"
          name="validUntil"
          type="datetime-local"
          value={validUntil}
          error={problems.validUntil}
          onChange={(event) => {
            setValidUntil(event.target.value);
          }}
        />
        <p data-testid="total-preview" aria-live="polite">
          {preview === undefined
            ? "Enter a delivery price to see the total the customer will be asked to pay."
            : `Order total the customer will be asked to pay: ${amount(preview, currency)}`}
        </p>
        <Button type="submit" disabled={busy}>
          Review and send terms
        </Button>
      </form>
      {request ? (
        <ConfirmDialog
          title="Send these terms?"
          confirmLabel="Send terms"
          busy={busy}
          onCancel={() => {
            setRequest(undefined);
          }}
          onConfirm={() => {
            onSubmit(request);
            setRequest(undefined);
          }}
        >
          <p>
            {`The customer will be emailed and asked to pay ${amount(
              addDecimals(order.net, order.tax, request.deliveryPrice),
              currency,
            )} for order ${order.summary.number}. The order returns to awaiting payment.`}
          </p>
        </ConfirmDialog>
      ) : null}
    </section>
  );
}
