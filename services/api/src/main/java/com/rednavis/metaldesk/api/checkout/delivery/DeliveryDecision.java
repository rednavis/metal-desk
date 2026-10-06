package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.share.domain.fulfillment.TierEvaluation;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.time.Instant;
import java.util.Optional;

/**
 * Turns the outcome of {@code TierSelector} into a session's {@link DeliveryState}.
 *
 * <p>An accepting tier means payment is allowed with its quote. Exceeding the widest tier means a
 * manager, with the ceiling that bound as the reason (value takes precedence when both did, as
 * {@code TierSelector} decides). A region with no tier at all is a manager too, with its own
 * reason: it is never a zero-cost quote.
 */
public final class DeliveryDecision {

  private DeliveryDecision() {}

  /**
   * Decides the stage.
   *
   * @param evaluation what the selector returned
   * @param exTaxValue the order value before tax it was evaluated on
   * @param weight the weight it was evaluated on
   * @param now when it was evaluated
   * @return the state to record on the session
   */
  public static DeliveryState of(
      TierEvaluation evaluation, Money exTaxValue, Weight weight, Instant now) {
    return evaluation instanceof TierEvaluation.Priced priced
        ? new DeliveryState(
            CheckoutStage.PAYMENT_ALLOWED,
            Optional.of(priced.quote()),
            Optional.empty(),
            exTaxValue,
            weight,
            now)
        : manager(reason(evaluation), exTaxValue, weight, now);
  }

  private static HandoffReason reason(TierEvaluation evaluation) {
    HandoffReason reason = HandoffReason.NO_TIER_FOR_REGION;
    if (evaluation instanceof TierEvaluation.ExceedsCeiling exceeded) {
      reason =
          switch (exceeded.which()) {
            case VALUE -> HandoffReason.VALUE_CEILING_EXCEEDED;
            case WEIGHT -> HandoffReason.WEIGHT_CEILING_EXCEEDED;
          };
    }
    return reason;
  }

  private static DeliveryState manager(
      HandoffReason reason, Money exTaxValue, Weight weight, Instant now) {
    return new DeliveryState(
        CheckoutStage.HANDOFF_REQUIRED,
        Optional.empty(),
        Optional.of(reason),
        exTaxValue,
        weight,
        now);
  }
}
