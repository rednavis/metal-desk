package com.rednavis.metaldesk.pricingbridge.feed.fake;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the {@link FakeMarketDataFeed}, under {@code metaldesk.pricing-bridge.fake-feed}.
 *
 * <p>The metals the fake quotes are exactly the keys of {@code startingPrices}. The prices are
 * invented and mean nothing; they exist so that a storefront has something to show.
 *
 * @param interval the time between two rounds of ticks, positive
 * @param startingPrices the first price per gram of each metal, in euro, each greater than zero
 * @param volatility the largest relative move of a price in one round, as a fraction: {@code 0.01}
 *     is one percent; greater than zero and less than one
 * @param seed the seed of the random walk; the same seed always gives the same price sequence
 */
@ConfigurationProperties("metaldesk.pricing-bridge.fake-feed")
public record FakeFeedProperties(
    @DefaultValue("2s") Duration interval,
    Map<Metal, BigDecimal> startingPrices,
    @DefaultValue("0.005") BigDecimal volatility,
    @DefaultValue("20260930") long seed) {

  /**
   * Validates and copies the settings.
   *
   * @throws ValidationException if the interval is not positive, there is no starting price, a
   *     starting price is not positive, or the volatility is outside (0, 1)
   */
  public FakeFeedProperties {
    if (interval == null || interval.isZero() || interval.isNegative()) {
      throw new ValidationException("fake-feed.interval-invalid", "Interval must be positive");
    }
    if (startingPrices == null || startingPrices.isEmpty()) {
      throw new ValidationException(
          "fake-feed.prices-missing", "The fake feed needs at least one starting price");
    }
    if (startingPrices.values().stream().anyMatch(price -> price.signum() <= 0)) {
      throw new ValidationException(
          "fake-feed.price-invalid", "Starting prices must be greater than zero");
    }
    if (volatility == null
        || volatility.signum() <= 0
        || volatility.compareTo(BigDecimal.ONE) >= 0) {
      throw new ValidationException(
          "fake-feed.volatility-invalid", "Volatility must be greater than 0 and less than 1");
    }
    startingPrices = Map.copyOf(startingPrices);
  }
}
