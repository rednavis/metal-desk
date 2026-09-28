package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The delivery cost quoted for an order (BRD BR-8).
 *
 * <p><strong>Interim shape.</strong> T-014 needs an order to hold a quote, so this type exists with
 * only what the order total needs: the cost. T-016 owns it and adds the tier applied, the transit
 * time and the instant quoted; it should replace this file, keeping {@link #cost()}.
 *
 * <p>The quote carries exactly one cost. Insurance is folded into delivery cost and is never a
 * separate line (BRD BR-7, FR-5.4), so an insurance component must not be added here.
 *
 * @param cost the delivery cost, insurance included, never null and never negative
 */
public record DeliveryQuote(Money cost) {

  /**
   * Validates the cost.
   *
   * @throws ValidationException if the cost is null or negative
   */
  public DeliveryQuote {
    if (cost == null) {
      throw new ValidationException(
          "delivery-quote.cost-missing", "Delivery cost must not be null");
    }
    if (cost.isNegative()) {
      throw new ValidationException(
          "delivery-quote.cost-negative", "Delivery cost must not be negative");
    }
  }
}
