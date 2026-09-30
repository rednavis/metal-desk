package com.rednavis.metaldesk.pricingbridge.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.feed.MarketDataFeed;
import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** The subscription sheds load by keeping the newest price, and survives the feed failing. */
class SubscriptionManagerTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final Duration INITIAL = Duration.ofSeconds(1);
  private static final SubscriptionProperties PROPERTIES =
      new SubscriptionProperties(INITIAL, Duration.ofSeconds(8), 0, Duration.ofSeconds(30));
  private static final RandomGenerator JITTER_SOURCE = RandomGenerator.of("L64X128MixRandom");

  private final ReferencePriceBook book = new ReferencePriceBook(Clock.systemUTC());

  private static ReferencePriceTick tick(Metal metal, long seconds) {
    return new ReferencePriceTick(
        metal,
        Money.of(BigDecimal.valueOf(seconds), Currency.EUR),
        T0.plusSeconds(seconds),
        "test");
  }

  private static <T> List<T> collect(Flux<T> flux) {
    return Objects.requireNonNull(flux.collectList().block(Duration.ofSeconds(30)));
  }

  private SubscriptionManager manager(MarketDataFeed feed) {
    // jitter is 0 in PROPERTIES, so the random value cannot change a delay
    return new SubscriptionManager(feed, book, PROPERTIES, JITTER_SOURCE);
  }

  @Test
  void slowConsumerSeesFewerTicksButAlwaysLatestOfEachMetal() {
    final int total = 300;
    final Flux<ReferencePriceTick> fast =
        Flux.interval(Duration.ofMillis(1))
            .take(total)
            .map(index -> tick(index % 2 == 0 ? Metal.GOLD : Metal.SILVER, index + 1));

    final List<ReferencePriceTick> seen =
        collect(
            SubscriptionManager.latestPerMetal(fast)
                .concatMap(tick -> Mono.delay(Duration.ofMillis(20)).thenReturn(tick), 1));

    assertTrue(seen.size() < total / 2, "load was shed, saw " + seen.size());
    assertEquals(
        Money.of("299", Currency.EUR),
        seen.stream()
            .filter(t -> t.metal() == Metal.GOLD)
            .reduce((a, b) -> b)
            .orElseThrow()
            .pricePerGram());
    assertEquals(
        Money.of("300", Currency.EUR),
        seen.stream()
            .filter(t -> t.metal() == Metal.SILVER)
            .reduce((a, b) -> b)
            .orElseThrow()
            .pricePerGram());
  }

  @Test
  void consumerThatKeepsUpSeesEveryTick() {
    final List<ReferencePriceTick> all = new ArrayList<>();
    for (long n = 1; n <= 50; n++) {
      all.add(tick(n % 2 == 0 ? Metal.GOLD : Metal.SILVER, n));
    }

    final List<ReferencePriceTick> seen =
        collect(SubscriptionManager.latestPerMetal(Flux.fromIterable(all)));

    assertEquals(all.size(), seen.size());
  }

  @Test
  void resubscribesWithDelaysThatGrowAndStopGrowingAtTheCap() {
    final List<Long> subscribedAt = new CopyOnWriteArrayList<>();
    final MarketDataFeed failing =
        () ->
            Flux.defer(
                () -> {
                  subscribedAt.add(Schedulers.parallel().now(TimeUnit.MILLISECONDS));
                  return Flux.error(new IllegalStateException("connection refused"));
                });

    StepVerifier.withVirtualTime(() -> manager(failing).subscription())
        .thenAwait(Duration.ofSeconds(23))
        .thenCancel()
        .verify();

    final List<Long> gaps = new ArrayList<>();
    for (int i = 1; i < subscribedAt.size(); i++) {
      gaps.add(subscribedAt.get(i) - subscribedAt.get(i - 1));
    }
    assertEquals(List.of(1000L, 2000L, 4000L, 8000L, 8000L), gaps);
  }

  @Test
  void feedThatCompletesIsReconnectedLikeOneThatFailed() {
    final List<Long> subscribedAt = new CopyOnWriteArrayList<>();
    final MarketDataFeed ending =
        () ->
            Flux.defer(
                () -> {
                  subscribedAt.add(Schedulers.parallel().now(TimeUnit.MILLISECONDS));
                  return Flux.empty();
                });

    StepVerifier.withVirtualTime(() -> manager(ending).subscription())
        .thenAwait(Duration.ofSeconds(3))
        .thenCancel()
        .verify();

    assertEquals(3, subscribedAt.size());
  }

  @Test
  void theDelayStartsOverOnceTicksArriveAgain() {
    final List<Long> subscribedAt = new CopyOnWriteArrayList<>();
    final MarketDataFeed oneTickThenFail =
        () ->
            Flux.defer(
                () -> {
                  subscribedAt.add(Schedulers.parallel().now(TimeUnit.MILLISECONDS));
                  return Flux.just(tick(Metal.GOLD, subscribedAt.size()))
                      .concatWith(
                          Mono.delay(Duration.ofMillis(100))
                              .then(Mono.error(new IllegalStateException("dropped"))));
                });

    StepVerifier.withVirtualTime(
            () -> manager(oneTickThenFail).subscription().take(Duration.ofSeconds(10)))
        .thenAwait(Duration.ofSeconds(10))
        .thenConsumeWhile(tick -> true)
        .verifyComplete();

    for (int i = 1; i < subscribedAt.size(); i++) {
      // 100 ms of a live connection, then the initial delay again, never a doubled one
      assertEquals(1100L, subscribedAt.get(i) - subscribedAt.get(i - 1));
    }
    assertTrue(subscribedAt.size() > 5, "subscriptions: " + subscribedAt);
  }

  @Test
  void recordsWhatArrivesInTheBook() {
    final MarketDataFeed feed = () -> Flux.just(tick(Metal.GOLD, 1)).concatWith(Flux.never());

    StepVerifier.create(manager(feed).subscription())
        .expectNextCount(1)
        .thenCancel()
        .verify(Duration.ofSeconds(5));

    assertEquals(
        Money.of("1", Currency.EUR), book.find(Metal.GOLD).orElseThrow().latest().pricePerGram());
    assertTrue(book.lastTickAt().isPresent());
  }

  @Test
  void lifecycleStartsAndStopsTheSubscription() {
    final SubscriptionManager manager =
        manager(() -> Flux.just(tick(Metal.GOLD, 1)).concatWith(Flux.never()));

    manager.start();
    assertTrue(manager.isRunning());
    manager.stop();
    assertFalse(manager.isRunning());
  }
}
