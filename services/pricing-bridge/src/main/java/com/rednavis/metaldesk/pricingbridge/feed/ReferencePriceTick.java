package com.rednavis.metaldesk.pricingbridge.feed;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;

/**
 * One observation of a metal's price as delivered by a {@link MarketDataFeed}.
 *
 * <p>It is the feed's wire-level fact; the domain's {@link ReferencePrice} is what the rest of the
 * platform reasons about. The only difference is {@code source}, which says which feed produced the
 * tick so that a price seen in production can be traced back to it.
 *
 * @param metal the metal, never null
 * @param pricePerGram the price of one gram of pure metal, never null
 * @param observedAt when the price was observed, never null
 * @param source the name of the feed that produced the tick, never blank
 */
public record ReferencePriceTick(
    Metal metal, Money pricePerGram, Instant observedAt, String source) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is null or the source is blank
   */
  public ReferencePriceTick {
    if (metal == null || pricePerGram == null || observedAt == null) {
      throw new ValidationException(
          "tick.field-missing", "A tick needs a metal, a price and an observation time");
    }
    if (source == null || source.isBlank()) {
      throw new ValidationException("tick.source-missing", "A tick must name its source");
    }
  }

  /**
   * Converts the tick to the domain's reference price.
   *
   * @return the reference price; {@link ReferencePrice} itself refuses a non-positive price
   */
  public ReferencePrice toReferencePrice() {
    return new ReferencePrice(metal, pricePerGram, observedAt);
  }
}
