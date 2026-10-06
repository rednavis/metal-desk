package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;

/**
 * A live market price for one metal (BRD BR-3 "spot price", FR-1.1): what a gram of the
 * <em>pure</em> metal costs, and when that was observed.
 *
 * <p>The price is per gram, the canonical weight unit, so it can be multiplied directly by a
 * product's canonical weight. The observation instant is part of the value rather than metadata:
 * FR-1.1 shows the direction and magnitude of the latest change, which needs the time, and BR-2's
 * price finality means "the price at that moment" must be answerable. This package has no clock;
 * whoever reads the feed supplies the instant.
 *
 * <p>The price is a {@link Money}, so it is held to its currency's minor unit (cents). That is
 * coarse for a cheap metal quoted per gram, so a feed adapter must not round a quote before it
 * reaches here more than the currency requires.
 *
 * @param metal the metal the price is for, never null
 * @param pricePerGram the price of one gram of pure metal, never null and greater than zero
 * @param observedAt when the price was observed, never null
 */
public record ReferencePrice(Metal metal, Money pricePerGram, Instant observedAt) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if any field is null, or the price is zero or negative
   */
  public ReferencePrice {
    if (metal == null) {
      throw new ValidationException("reference-price.metal-missing", "Metal must not be null");
    }
    if (pricePerGram == null) {
      throw new ValidationException(
          "reference-price.price-missing", "Reference price must not be null");
    }
    if (pricePerGram.isZero() || pricePerGram.isNegative()) {
      throw new ValidationException(
          "reference-price.not-positive", "Reference price must be greater than zero");
    }
    if (observedAt == null) {
      throw new ValidationException(
          "reference-price.instant-missing", "Observation instant must not be null");
    }
  }
}
