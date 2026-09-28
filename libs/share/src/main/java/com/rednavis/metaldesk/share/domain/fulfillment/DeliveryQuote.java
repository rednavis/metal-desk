package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;

/**
 * The delivery cost quoted for an order (BRD BR-8): which tier priced it, what it costs, how long
 * it takes, and when it was quoted.
 *
 * <p>The quote copies the tier's price and transit time rather than pointing at the tier, so it is
 * a snapshot: reconfiguring the tier later cannot change a quote already given (see BRD BR-2). The
 * tier's id is kept for support to see where the figure came from.
 *
 * <p>The quote carries exactly one cost. Insurance is folded into delivery cost and is never a
 * separate line (BRD BR-7, FR-5.4), so an insurance component must not be added here.
 *
 * @param tierId the tier that priced the order, never null
 * @param cost the delivery cost, insurance included, never null and never negative
 * @param transit the estimated transit time, never null
 * @param quotedAt when the quote was made, never null
 */
public record DeliveryQuote(
    FulfillmentTierId tierId, Money cost, TransitTime transit, Instant quotedAt) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null or the cost is negative
   */
  public DeliveryQuote {
    if (tierId == null || cost == null || transit == null || quotedAt == null) {
      throw new ValidationException(
          "delivery-quote.field-missing", "Delivery quote requires a tier, cost, transit and time");
    }
    if (cost.isNegative()) {
      throw new ValidationException(
          "delivery-quote.cost-negative", "Delivery cost must not be negative");
    }
  }

  /**
   * Quotes an order at a tier's price.
   *
   * @param tier the tier that prices the order
   * @param quotedAt when the quote is made, supplied by the caller because the domain has no clock
   * @return the quote, copying the tier's price and transit time
   * @throws ValidationException if the tier or instant is null
   */
  public static DeliveryQuote from(FulfillmentTier tier, Instant quotedAt) {
    if (tier == null) {
      throw new ValidationException("delivery-quote.field-missing", "Tier must not be null");
    }
    return new DeliveryQuote(tier.id(), tier.deliveryPrice(), tier.transit(), quotedAt);
  }
}
