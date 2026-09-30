import { useEffect, useRef } from "react";
import { usePreferences } from "../../../preferences/usePreferences";
import { Button, ErrorState, Spinner } from "../../../ui";
import { HandoffOutcome } from "../HandoffOutcome";
import type { CheckoutApi } from "../useCheckoutSession";

/**
 * Step 2 (BRD FR-5.2, FR-5.3): the delivery evaluation, and the branch it decides.
 *
 * The evaluation runs whenever the session has none, which is on first arrival and again after any
 * edit of step 1 (the server drops it then), so a quote from an earlier address is never on screen.
 *
 * - **Payment allowed:** the quoted cost and transit time, and the way on to choosing how to pay.
 * - **Manager required:** the primary action is the handoff. The reason the server gave is explained,
 *   naming the ceiling that bound, and nothing on the screen offers to take a payment.
 * - **Handed over:** the receipt with the reference number.
 */
export function Step2Delivery({
  checkout,
  onContinue,
  onEditDetails,
}: {
  checkout: CheckoutApi;
  onContinue: () => void;
  onEditDetails: () => void;
}) {
  const { t, format } = usePreferences();
  const session = checkout.session.data;
  const delivery = session?.delivery;
  const { evaluate, handoff } = checkout;
  const started = useRef(false);

  useEffect(() => {
    if (delivery === undefined && !started.current) {
      started.current = true;
      evaluate.mutate();
    }
  }, [delivery, evaluate]);

  const handedOff = delivery?.handoffReference ?? handoff.data?.reference;
  if (handedOff !== undefined) return <HandoffOutcome reference={handedOff} />;

  if (evaluate.isError && delivery === undefined) {
    return (
      <ErrorState
        error={evaluate.error}
        action={
          <Button
            onClick={() => {
              evaluate.mutate();
            }}
          >
            {t("checkout.error.retry")}
          </Button>
        }
      />
    );
  }
  if (delivery === undefined) return <Spinner label={t("checkout.delivery.calculating")} />;

  const conversion = session?.conversion;
  const notice =
    conversion && !conversion.verified ? (
      <p role="status">{t("checkout.conversion.notice", { reference: conversion.reference })}</p>
    ) : null;

  if (delivery.stage === "PAYMENT_ALLOWED" && delivery.quote) {
    const { cost, minDays, maxDays } = delivery.quote;
    return (
      <section aria-labelledby="step2-title">
        <h2 id="step2-title">{t("checkout.step2.title")}</h2>
        {notice}
        <dl>
          <dt>{t("checkout.delivery.cost")}</dt>
          <dd data-testid="delivery-cost">{format.money(cost)}</dd>
          <dt>{t("checkout.delivery.transit")}</dt>
          <dd>{t("checkout.delivery.days", { min: minDays, max: maxDays })}</dd>
        </dl>
        <div className="md-actions">
          <Button variant="secondary" onClick={onEditDetails}>
            {t("checkout.back")}
          </Button>
          <Button onClick={onContinue}>{t("checkout.delivery.continue")}</Button>
        </div>
      </section>
    );
  }

  const reason = delivery.reason ?? "NO_TIER_FOR_REGION";
  return (
    <section aria-labelledby="step2-title" data-testid="handoff-required">
      <h2 id="step2-title">{t("checkout.handoff.title")}</h2>
      {notice}
      <p>
        {t(`checkout.handoff.reason.${reason}`, {
          value: format.money(delivery.exTaxValue),
          weight: t("product.weight.GRAM", {
            amount: format.number(Number(delivery.weightGrams)),
          }),
        })}
      </p>
      {delivery.boundCeiling ? (
        <p data-testid="bound-ceiling">{t(`checkout.handoff.ceiling.${delivery.boundCeiling}`)}</p>
      ) : null}
      <p>{t("checkout.handoff.explain")}</p>
      {handoff.isError ? <ErrorState error={handoff.error} /> : null}
      <div className="md-actions">
        <Button variant="secondary" onClick={onEditDetails}>
          {t("checkout.back")}
        </Button>
        <Button
          disabled={handoff.isPending}
          onClick={() => {
            handoff.mutate();
          }}
        >
          {t("checkout.handoff.action")}
        </Button>
      </div>
    </section>
  );
}
