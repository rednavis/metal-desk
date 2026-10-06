package com.rednavis.metaldesk.pricingbridge.subscription;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * The latest reference price of each metal and the one before it, so that the storefront can show
 * the direction and size of the last change (BRD FR-1.1), plus when a tick last arrived.
 *
 * <p>A metal with one observation has no previous one: {@link Observation#previous()} is empty and
 * a caller reports the change as absent, never as zero. A tick observed at the same instant as the
 * latest is a repeat of it and does not displace the previous price, but it still counts as a sign
 * of life for {@link #lastTickAt()}.
 *
 * <p>In memory and per instance: only the last two observations are kept (a price history is out of
 * scope), so the book cannot grow beyond two prices per metal.
 */
@Component
public class ReferencePriceBook {

  private final Clock clock;
  private final Instant createdAt;
  private final ConcurrentMap<Metal, Observation> observations = new ConcurrentHashMap<>();
  private final AtomicReference<Instant> lastArrival = new AtomicReference<>();

  /**
   * Creates an empty book.
   *
   * @param clock the clock that stamps tick arrival
   */
  public ReferencePriceBook(Clock clock) {
    this.clock = clock;
    this.createdAt = clock.instant();
  }

  /** The latest price of a metal and the one before it. */
  public record Observation(ReferencePrice latest, Optional<ReferencePrice> previous) {}

  /**
   * Records a tick, keeping the price it replaces as the previous one.
   *
   * @param tick the tick that arrived
   */
  public void record(ReferencePriceTick tick) {
    lastArrival.set(clock.instant());
    final ReferencePrice price = tick.toReferencePrice();
    observations.merge(
        price.metal(),
        new Observation(price, Optional.empty()),
        (held, incoming) ->
            held.latest().observedAt().equals(price.observedAt())
                ? held
                : new Observation(price, Optional.of(held.latest())));
  }

  /**
   * Looks up one metal.
   *
   * @param metal the metal
   * @return its observations, or empty if none has been seen
   */
  public Optional<Observation> find(Metal metal) {
    return Optional.ofNullable(observations.get(metal));
  }

  /**
   * Lists every metal seen so far, in the order of {@link Metal}.
   *
   * @return the observations; empty on a cold start
   */
  public List<Observation> all() {
    return List.of(Metal.values()).stream().flatMap(metal -> find(metal).stream()).toList();
  }

  /**
   * When a tick last arrived, by this service's clock.
   *
   * @return the arrival time, or empty if nothing has arrived since start
   */
  public Optional<Instant> lastTickAt() {
    return Optional.ofNullable(lastArrival.get());
  }

  /**
   * When the book was created, which is the start of the wait for a first tick.
   *
   * @return the start instant
   */
  public Instant startedAt() {
    return createdAt;
  }
}
