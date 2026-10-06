package com.rednavis.metaldesk.pricingbridge.feed.fake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/** The fake feed is a real stream, bounded, and reproducible from its seed. */
class FakeMarketDataFeedTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);
  private static final int ROUNDS = 40;

  private static FakeFeedProperties properties(long seed) {
    return new FakeFeedProperties(
        Duration.ofMillis(1),
        Map.of(Metal.GOLD, new BigDecimal("60.00"), Metal.SILVER, new BigDecimal("0.80")),
        new BigDecimal("0.02"),
        seed);
  }

  private static <T> List<T> collect(Flux<T> flux) {
    return Objects.requireNonNull(flux.collectList().block(Duration.ofSeconds(30)));
  }

  private static List<String> sequence(long seed) {
    return collect(
        new FakeMarketDataFeed(properties(seed), CLOCK)
            .ticks()
            .take(ROUNDS * 2L)
            .map(tick -> tick.metal() + "=" + tick.pricePerGram().amount()));
  }

  @Test
  void sameSeedGivesTheSameSequenceOnEveryRun() {
    assertEquals(sequence(7), sequence(7));
  }

  @Test
  void differentSeedsGiveDifferentSequences() {
    assertNotEquals(sequence(7), sequence(8));
  }

  @Test
  void reconnectRestartsWalkFromSeed() {
    final FakeMarketDataFeed feed = new FakeMarketDataFeed(properties(7), CLOCK);
    final List<ReferencePriceTick> first = collect(feed.ticks().take(20));
    final List<ReferencePriceTick> second = collect(feed.ticks().take(20));

    assertEquals(first, second);
  }

  @Test
  void firstRoundIsTheStartingPricesAndEveryTickIsNamedAndPositive() {
    final List<ReferencePriceTick> ticks =
        collect(new FakeMarketDataFeed(properties(1), CLOCK).ticks().take(2));

    assertEquals(new BigDecimal("60.00"), ticks.get(0).pricePerGram().amount());
    assertEquals(Metal.GOLD, ticks.get(0).metal());
    assertEquals(new BigDecimal("0.80"), ticks.get(1).pricePerGram().amount());
    assertTrue(ticks.stream().allMatch(tick -> FakeMarketDataFeed.SOURCE.equals(tick.source())));
  }

  @Test
  void pricesStayWithinHalfToOnePointFiveTimesStart() {
    final List<ReferencePriceTick> ticks =
        collect(
            new FakeMarketDataFeed(properties(3), CLOCK)
                .ticks()
                .filter(tick -> tick.metal() == Metal.GOLD)
                .take(200));

    assertTrue(
        ticks.stream()
            .allMatch(
                tick ->
                    tick.pricePerGram().amount().compareTo(new BigDecimal("30.00")) >= 0
                        && tick.pricePerGram().amount().compareTo(new BigDecimal("90.00")) <= 0));
  }

  @Test
  void emitsOneRoundImmediatelyThenOnePerInterval() {
    final FakeFeedProperties slow =
        new FakeFeedProperties(
            Duration.ofSeconds(5),
            Map.of(Metal.GOLD, new BigDecimal("60.00")),
            new BigDecimal("0.01"),
            1);

    StepVerifier.withVirtualTime(() -> new FakeMarketDataFeed(slow, CLOCK).ticks())
        .expectNextCount(1)
        .expectNoEvent(Duration.ofSeconds(4))
        .thenAwait(Duration.ofSeconds(1))
        .expectNextCount(1)
        .expectNoEvent(Duration.ofSeconds(4))
        .thenAwait(Duration.ofSeconds(1))
        .expectNextCount(1)
        .thenCancel()
        .verify();
  }

  @Test
  void theFeedStaysOpen() {
    final Flux<ReferencePriceTick> ticks = new FakeMarketDataFeed(properties(1), CLOCK).ticks();

    StepVerifier.create(ticks.take(Duration.ofMillis(50)))
        .thenConsumeWhile(tick -> true)
        .verifyComplete();
  }
}
