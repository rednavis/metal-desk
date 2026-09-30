package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * The in-process stand-in for the market-data feed, used in dev and CI (ADR-0002).
 *
 * <p>Each metal has a fixed synthetic price per gram, and on the n-th fetch it is moved by a gentle
 * wave — at most three percent either way, out of step between metals — so a run is repeatable and
 * the direction and size of the last change (FR-1.1) actually vary from one poll to the next. The
 * prices are invented and mean nothing.
 */
@Component
public class FakeMarketDataClient implements MarketDataClient {

  private static final double SWING = 0.03;
  private static final double PACE = 0.7;
  private static final int SCALE = 2;

  private final Clock clock;
  private final AtomicLong fetches = new AtomicLong();

  /**
   * Creates the fake.
   *
   * @param clock the source of observation times
   */
  public FakeMarketDataClient(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Flux<ReferencePrice> fetchLatest() {
    final long fetch = fetches.incrementAndGet();
    final Instant observedAt = clock.instant();
    return Flux.fromArray(Metal.values())
        .map(
            metal ->
                new ReferencePrice(
                    metal, Money.of(priceOn(metal, fetch), Currency.EUR), observedAt));
  }

  private static BigDecimal priceOn(Metal metal, long fetch) {
    final double wave = Math.sin(fetch * PACE + metal.ordinal());
    return startingPrice(metal)
        .multiply(BigDecimal.valueOf(1 + SWING * wave))
        .setScale(SCALE, RoundingMode.HALF_UP)
        .max(new BigDecimal("0.01"));
  }

  private static BigDecimal startingPrice(Metal metal) {
    return switch (metal) {
      case GOLD -> new BigDecimal("60.00");
      case SILVER -> new BigDecimal("0.80");
      case PLATINUM -> new BigDecimal("30.00");
      case PALLADIUM -> new BigDecimal("32.00");
      case RHODIUM -> new BigDecimal("140.00");
      case RUTHENIUM -> new BigDecimal("14.00");
    };
  }
}
