import { useId, useState } from "react";
import { useLocation } from "react-router";
import type { PaymentMethod } from "../../../api/types";
import { usePreferences } from "../../../preferences/usePreferences";
import { Button, EmptyState, ErrorState, Spinner } from "../../../ui";
import { CheckoutSummary } from "../CheckoutSummary";
import type { CheckoutApi } from "../useCheckoutSession";

/** A decorative glyph per method; the name beside it is what identifies the method. */
const CHECK_GLYPH = "✓";

const METHOD_GLYPH: Record<PaymentMethod, string> = {
  CARD: "▭",
  BANK_DEBIT: "⇄",
  BANK_REDIRECT: "↗",
  BANK_TRANSFER: "⇄",
  SAVED_WALLET: "◈",
  WALLET_ACCOUNT: "◈",
  INVOICE: "☰",
};

/**
 * Step 3 (BRD FR-6.1, FR-6.2, FR-6.3): choose how to pay, from exactly the list the server offers.
 *
 * The list depends on the order (BR-9: a high-value order is not offered the gateway methods), so
 * none of it is written here: what the server sends is what is shown, and the invoice is one more
 * choice in the same list, not a fallback under it.
 *
 * It is also where a declined payment lands. The summary of the basket, details, delivery quote and
 * chosen method is shown beside the reason, all of it still there, so trying again costs one click.
 */
export function Step3PaymentMethod({
  checkout,
  onBack,
  onChosen,
}: {
  checkout: CheckoutApi;
  onBack: () => void;
  onChosen: () => void;
}) {
  const { t } = usePreferences();
  const idPrefix = useId();
  const location = useLocation();
  const decline = (location.state as { decline?: string } | null)?.decline;
  const { methods, selectMethod } = checkout;
  const session = checkout.session.data;
  const [choice, setChoice] = useState<PaymentMethod | undefined>();

  if (methods.isLoading || session === undefined) {
    return <Spinner label={t("checkout.loading")} />;
  }
  if (methods.isError) return <ErrorState error={methods.error} />;
  const offer = methods.data;
  if (offer === undefined) return <Spinner label={t("checkout.loading")} />;

  const current = choice ?? offer.selected;
  return (
    <div className="md-split">
      <section aria-labelledby="step3-title">
        <h2 id="step3-title">{t("checkout.step3.title")}</h2>
        {decline ? (
          <div role="alert" data-testid="decline-notice">
            <strong>{t("checkout.decline.title")}</strong>
            <p>{t("checkout.decline.body", { reason: decline })}</p>
          </div>
        ) : null}
        {offer.highValue ? <p role="note">{t("checkout.method.highValue")}</p> : null}
        {offer.methods.length === 0 ? (
          <EmptyState title={t("checkout.method.none")} />
        ) : (
          <form
            onSubmit={(event) => {
              event.preventDefault();
              if (current === undefined) return;
              selectMethod.mutate(current, { onSuccess: onChosen });
            }}
          >
            <fieldset className="md-methods">
              <legend>{t("checkout.method.legend")}</legend>
              {offer.methods.map(({ method, group }) => (
                <label key={method} className="md-method">
                  <input
                    type="radio"
                    name="payment-method"
                    value={method}
                    aria-labelledby={`${idPrefix}-${method}-name`}
                    aria-describedby={
                      group === "INVOICE" ? `${idPrefix}-${method}-note` : undefined
                    }
                    checked={current === method}
                    onChange={() => {
                      setChoice(method);
                    }}
                  />
                  <span className="md-method__glyph" aria-hidden="true">
                    {METHOD_GLYPH[method]}
                  </span>
                  <span className="md-method__text">
                    <span className="md-method__name" id={`${idPrefix}-${method}-name`}>
                      {t(`checkout.method.${method}`)}
                    </span>
                    {group === "INVOICE" ? (
                      <span className="md-method__note" id={`${idPrefix}-${method}-note`}>
                        {t("checkout.method.INVOICE.note")}
                      </span>
                    ) : null}
                  </span>
                  <span className="md-method__check" aria-hidden="true">
                    {CHECK_GLYPH}
                  </span>
                </label>
              ))}
            </fieldset>
            {selectMethod.isError ? <ErrorState error={selectMethod.error} /> : null}
            <div className="md-actions">
              <Button variant="secondary" onClick={onBack}>
                {t("checkout.back")}
              </Button>
              <Button type="submit" disabled={current === undefined || selectMethod.isPending}>
                {t("checkout.continue")}
              </Button>
            </div>
          </form>
        )}
      </section>
      <CheckoutSummary session={session} method={offer.selected} />
    </div>
  );
}
