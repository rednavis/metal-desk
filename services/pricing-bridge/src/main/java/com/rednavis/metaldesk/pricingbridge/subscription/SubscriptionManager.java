package com.rednavis.metaldesk.pricingbridge.subscription;

import com.rednavis.metaldesk.pricingbridge.feed.MarketDataFeed;
import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Owns the long-lived subscription to the {@link MarketDataFeed}: opens it at startup, keeps it
 * open, and feeds what arrives into the {@link ReferencePriceBook}.
 *
 * <p><strong>Backpressure: the newest price per metal wins, and nothing is buffered.</strong> A
 * market-data stream is always faster than a consumer wants, and for a price only the most recent
 * value matters, so a tick that has been overtaken by a newer tick of the same metal is dropped
 * (see {@link #latestPerMetal(Flux)}). Each metal holds at most one pending tick, so memory is
 * bounded by the number of metals however slow the consumer is. An unbounded buffer would turn a
 * slow consumer into an out-of-memory kill in the smallest container; it is deliberately not used.
 * The bound is per metal, not global, because dropping gold's tick for silver's would leave gold
 * stale.
 *
 * <p><strong>Reconnect: bounded exponential backoff with jitter, forever.</strong> A feed that
 * errors <em>or completes</em> is a lost connection, because a healthy feed never ends. The
 * subscription is reopened after a delay that doubles from {@code initialBackoff} to {@code
 * maxBackoff} with jitter ({@link ReconnectBackoff}), and every attempt is logged at warn. The
 * delay is never zero, so there is no tight loop; it restarts from the initial value as soon as a
 * tick arrives, so a feed that recovered is not punished for an old outage. Giving up is not an
 * option — a storefront left showing a frozen price is worse than one that shows none — so the
 * silence is reported instead through {@link FeedStatusEndpoint}.
 */
@Slf4j
@Component
public class SubscriptionManager implements SmartLifecycle {

  private final MarketDataFeed feed;
  private final ReferencePriceBook book;
  private final ReconnectBackoff backoff;
  private final RandomGenerator random;
  private final AtomicLong failures = new AtomicLong();
  private final AtomicReference<Disposable> connection = new AtomicReference<>();

  /**
   * Creates the manager.
   *
   * @param feed the feed to subscribe to
   * @param book where the ticks are recorded
   * @param properties the reconnect settings
   * @param random the source of reconnect jitter
   */
  public SubscriptionManager(
      MarketDataFeed feed,
      ReferencePriceBook book,
      SubscriptionProperties properties,
      RandomGenerator random) {
    this.feed = feed;
    this.book = book;
    this.backoff = properties.backoff();
    this.random = random;
  }

  /**
   * The resilient stream: the feed, reduced to the newest price per metal, recorded in the book,
   * and resubscribed whenever it ends. It never completes and never errors; cancel it to stop.
   *
   * @return the recorded ticks
   */
  public Flux<ReferencePriceTick> subscription() {
    return Flux.defer(
            () -> latestPerMetal(feed.ticks().concatWith(Flux.error(FeedEndedException::new))))
        .doOnNext(tick -> failures.set(0))
        .doOnNext(book::record)
        .retryWhen(reconnect());
  }

  /**
   * Keeps only the newest pending tick of each metal when the consumer falls behind.
   *
   * @param ticks the feed's ticks
   * @return the ticks, with overtaken ones dropped under backpressure; a consumer that keeps up
   *     sees every tick
   */
  public static Flux<ReferencePriceTick> latestPerMetal(Flux<ReferencePriceTick> ticks) {
    return ticks
        .groupBy(ReferencePriceTick::metal)
        .flatMap(Flux::onBackpressureLatest, Metal.values().length, 1);
  }

  private Retry reconnect() {
    return Retry.from(
        signals ->
            signals.concatMap(
                signal -> {
                  final long attempt = failures.getAndIncrement();
                  final Duration delay = backoff.delay(attempt, random.nextDouble());
                  final String reason = signal.failure().getMessage();
                  final long delayMillis = delay.toMillis();
                  final long attemptNumber = attempt + 1;
                  log.warn(
                      "Market-data feed lost ({}); resubscribing in {} ms (attempt {})",
                      reason,
                      delayMillis,
                      attemptNumber);
                  return Mono.delay(delay);
                }));
  }

  @Override
  public void start() {
    final Disposable opened = subscription().subscribe();
    if (!connection.compareAndSet(null, opened)) {
      opened.dispose();
    }
  }

  @Override
  public void stop() {
    final Disposable current = connection.getAndSet(null);
    if (current != null) {
      current.dispose();
    }
  }

  @Override
  public boolean isRunning() {
    return connection.get() != null;
  }

  /** Thrown into the stream when the feed completes, so that completion is treated as a loss. */
  private static final class FeedEndedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private FeedEndedException() {
      super("the feed completed");
    }
  }
}
