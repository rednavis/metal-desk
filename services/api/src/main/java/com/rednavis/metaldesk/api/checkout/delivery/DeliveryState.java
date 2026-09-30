package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The outcome of the last delivery evaluation of a session.
 *
 * <p>Exactly one of {@code quote} and {@code reason} is present: {@link
 * CheckoutStage#PAYMENT_ALLOWED} carries the quote, {@link CheckoutStage#HANDOFF_REQUIRED} the
 * reason. The value and weight it was evaluated on are kept, because the staff notification needs
 * them and they are what the customer is shown.
 *
 * @param stage what the session now permits
 * @param quote the automatic delivery quote, present only when payment is allowed
 * @param reason why a manager is needed, present only when it is
 * @param exTaxValue the order value before tax, which is what the tiers are evaluated on (BRD
 *     FR-5.1)
 * @param weight the total weight of the order
 * @param evaluatedAt when the tiers were read and evaluated
 */
public record DeliveryState(
    CheckoutStage stage,
    Optional<DeliveryQuote> quote,
    Optional<HandoffReason> reason,
    Money exTaxValue,
    Weight weight,
    Instant evaluatedAt) {

  /**
   * Validates the state.
   *
   * @throws ValidationException if a field is missing or the quote and reason do not match the
   *     stage
   */
  public DeliveryState {
    if (Stream.of(stage, quote, reason, exTaxValue, weight, evaluatedAt)
        .anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "delivery-state.field-missing", "A delivery state needs every field");
    }
    final boolean consistent =
        stage == CheckoutStage.PAYMENT_ALLOWED
            ? quote.isPresent() && reason.isEmpty()
            : quote.isEmpty() && reason.isPresent();
    if (!consistent) {
      throw new ValidationException(
          "delivery-state.inconsistent", "The quote and reason must match the stage");
    }
  }
}
