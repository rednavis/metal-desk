package com.rednavis.metaldesk.api.marketdata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import java.time.Instant;

/**
 * One row of the reference-price panel.
 *
 * @param metal the metal
 * @param pricePerGram the latest price per gram, as decimal text
 * @param currency the currency code of the price
 * @param observedAt when the price was observed
 * @param change the change since the previous observation; absent, not zero, until there are two
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferencePriceView(
    Metal metal, String pricePerGram, String currency, Instant observedAt, ChangeView change) {

  /** Which way a price last moved. */
  public enum Direction {
    /** The price rose. */
    UP,
    /** The price fell. */
    DOWN,
    /** The price was observed again at exactly the same value. */
    UNCHANGED
  }

  /**
   * The most recent change of a price.
   *
   * @param direction whether it rose, fell or stayed
   * @param amount the size of the change per gram, unsigned, as decimal text
   * @param percent the size of the change relative to the previous price, unsigned, in percent to
   *     two places
   */
  public record ChangeView(Direction direction, String amount, String percent) {}
}
