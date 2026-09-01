package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * A delivery tier staff configure for a region (Architecture section 3, BRD FR-5.1): the ceilings
 * an order must be within, the delivery price that then applies, and the estimated transit time.
 *
 * <p><strong>A tier is data, not code.</strong> Staff configure ceilings and prices and the
 * checkout evaluates against whatever is currently configured, so changing a tier never needs a
 * deploy. There is nothing about any particular region or amount in this type.
 *
 * <p>An order is <em>within</em> a tier when its ex-tax value and its weight are each at or below
 * the ceiling; a ceiling is inclusive, and an order exactly at it is still priced automatically.
 * The value ceiling and the delivery price are in one currency, so a tier cannot be half in euro
 * and half in dollars. The delivery price includes insurance (BRD BR-7).
 *
 * @param id the tier's identifier, never null
 * @param region the destination region the tier serves, never null
 * @param valueCeiling the highest ex-tax order value the tier prices, greater than zero
 * @param weightCeiling the highest order weight the tier prices, greater than zero
 * @param deliveryPrice the delivery cost for an order within both ceilings, never negative and in
 *     the currency of {@code valueCeiling}
 * @param transit the estimated transit time, never null
 */
public record FulfillmentTier(
    FulfillmentTierId id,
    Region region,
    Money valueCeiling,
    Weight weightCeiling,
    Money deliveryPrice,
    TransitTime transit) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null, a ceiling is not greater than zero, the
   *     delivery price is negative, or the price and value ceiling are in different currencies
   */
  public FulfillmentTier {
    if (id == null || region == null || transit == null) {
      throw new ValidationException(
          "fulfillment-tier.field-missing", "Tier requires an id, region and transit time");
    }
    requireCeilings(valueCeiling, weightCeiling);
    if (deliveryPrice == null || deliveryPrice.isNegative()) {
      throw new ValidationException(
          "fulfillment-tier.price-invalid", "Tier delivery price must be present and not negative");
    }
    if (deliveryPrice.currency() != valueCeiling.currency()) {
      throw new ValidationException(
          "fulfillment-tier.currency-mismatch",
          "Tier value ceiling and delivery price must be in one currency");
    }
  }

  private static void requireCeilings(Money valueCeiling, Weight weightCeiling) {
    if (valueCeiling == null
        || weightCeiling == null
        || valueCeiling.isZero()
        || valueCeiling.isNegative()
        || weightCeiling.amount().signum() == 0) {
      throw new ValidationException(
          "fulfillment-tier.ceiling-invalid",
          "Tier ceilings must be present and greater than zero");
    }
  }

  /**
   * Tells whether an order's value is above this tier's value ceiling.
   *
   * @param exTaxValue the order value <em>before tax</em> (BRD FR-5.1, BR-8)
   * @return {@code true} if the value is above the ceiling
   * @throws ValidationException if the value is null or in another currency
   */
  public boolean exceedsValue(Money exTaxValue) {
    return exTaxValue.compareTo(valueCeiling) > 0;
  }

  /**
   * Tells whether an order's weight is above this tier's weight ceiling.
   *
   * @param weight the order weight
   * @return {@code true} if the weight is above the ceiling
   */
  public boolean exceedsWeight(Weight weight) {
    return weight.compareTo(weightCeiling) > 0;
  }

  /**
   * Tells whether an order is within both ceilings, so this tier can price it.
   *
   * @param exTaxValue the order value <em>before tax</em>
   * @param weight the order weight
   * @return {@code true} if neither ceiling is exceeded
   */
  public boolean accepts(Money exTaxValue, Weight weight) {
    return !exceedsValue(exTaxValue) && !exceedsWeight(weight);
  }
}
