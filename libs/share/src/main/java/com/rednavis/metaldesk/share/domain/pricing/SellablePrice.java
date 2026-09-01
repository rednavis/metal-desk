package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * A derived unit price together with where it came from (BRD BR-3, BR-2).
 *
 * <p>This is a provenance record, not just a number. Because BR-2 makes the price at the moment of
 * ordering final, an order line snapshots the whole {@code SellablePrice} (T-014), so a settled
 * order can still answer "why this price": which rule and which reference quote produced it.
 *
 * @param unitPrice the price of one unit of the product, never null
 * @param rule the price rule that was applied, never null
 * @param reference the reference price it was derived from, never null; in the same currency as
 *     {@code unitPrice}
 */
public record SellablePrice(Money unitPrice, PriceRule rule, ReferencePrice reference) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if any field is null, or the unit price is not in the reference
   *     price's currency
   */
  public SellablePrice {
    if (unitPrice == null) {
      throw new ValidationException("sellable-price.price-missing", "Unit price must not be null");
    }
    if (rule == null) {
      throw new ValidationException("sellable-price.rule-missing", "Price rule must not be null");
    }
    if (reference == null) {
      throw new ValidationException(
          "sellable-price.reference-missing", "Reference price must not be null");
    }
    if (unitPrice.currency() != reference.pricePerGram().currency()) {
      throw new ValidationException(
          "sellable-price.currency-mismatch",
          "Unit price currency differs from its reference price currency");
    }
  }
}
