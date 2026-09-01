package com.rednavis.metaldesk.pricingbridge.feed.fake;

import com.rednavis.metaldesk.pricingbridge.feed.MarketDataFeed;
import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * A market-data feed that lives in the process: the stand-in used for local development and CI, so
 * that nothing has to reach an external provider to build or test (ADR-0002).
 *
 * <p>It is a real stream, not a poll wrapped in a {@code Mono}: a subscription stays open and emits
 * one round of ticks, one per configured metal, every {@link FakeFeedProperties#interval()}, the
 * first immediately. Each metal follows a bounded random walk from its starting price: a step moves
 * it by at most the configured volatility, and it is held within half to one and a half times its
 * starting price so a long run cannot drift to nonsense.
 *
 * <p><strong>Deterministic when seeded.</strong> Every subscription starts a fresh walk from the
 * configured seed, so the prices of the n-th round are the same on every run and on every
 * reconnect. Only the observation instants come from the clock.
 *
 * <p>Active unless {@code metaldesk.pricing-bridge.feed} names another feed; the fake is the only
 * one there is.
 */
@Component
@ConditionalOnProperty(
    name = "metaldesk.pricing-bridge.feed",
    havingValue = "fake",
    matchIfMissing = true)
public class FakeMarketDataFeed implements MarketDataFeed {

  /** The source name carried by every tick the fake produces. */
  public static final String SOURCE = "fake";

  private static final BigDecimal FLOOR = new BigDecimal("0.5");
  private static final BigDecimal CEILING = new BigDecimal("1.5");
  private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
  private static final int SCALE = 2;

  private final FakeFeedProperties properties;
  private final Clock clock;

  /**
   * Creates the feed.
   *
   * @param properties the walk's settings
   * @param clock the source of observation instants
   */
  public FakeMarketDataFeed(FakeFeedProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public Flux<ReferencePriceTick> ticks() {
    return Flux.defer(
        () -> {
          final RandomWalk walk = new RandomWalk(properties);
          return Flux.interval(Duration.ZERO, properties.interval())
              .concatMapIterable(round -> walk.round(clock.instant()));
        });
  }

  /** The state of one subscription's random walk. */
  private static final class RandomWalk {

    private final List<Metal> metals = new ArrayList<>();
    private final List<BigDecimal> starts = new ArrayList<>();
    private final List<BigDecimal> current = new ArrayList<>();
    private final BigDecimal volatility;
    private final Random random;
    private boolean first = true;

    private RandomWalk(FakeFeedProperties properties) {
      for (final Metal metal : Metal.values()) {
        final BigDecimal price = properties.startingPrices().get(metal);
        if (price != null) {
          metals.add(metal);
          starts.add(price);
          current.add(price);
        }
      }
      this.volatility = properties.volatility();
      this.random = new Random(properties.seed());
    }

    /** Advances every metal one step (none on the first round) and reports the prices. */
    private List<ReferencePriceTick> round(Instant at) {
      final List<ReferencePriceTick> ticks = new ArrayList<>();
      for (int i = 0; i < metals.size(); i++) {
        if (!first) {
          current.set(i, step(starts.get(i), current.get(i)));
        }
        final BigDecimal quoted =
            current.get(i).setScale(SCALE, RoundingMode.HALF_UP).max(MIN_PRICE);
        ticks.add(
            new ReferencePriceTick(metals.get(i), Money.of(quoted, Currency.EUR), at, SOURCE));
      }
      first = false;
      return ticks;
    }

    // The walk keeps its unrounded value: rounding each step to cents would freeze a cheap metal
    // whose step is smaller than a cent.
    private BigDecimal step(BigDecimal base, BigDecimal price) {
      final double move = (random.nextDouble() * 2 - 1) * volatility.doubleValue();
      final BigDecimal moved = price.multiply(BigDecimal.valueOf(1 + move), MathContext.DECIMAL64);
      return moved.max(base.multiply(FLOOR)).min(base.multiply(CEILING));
    }
  }
}
