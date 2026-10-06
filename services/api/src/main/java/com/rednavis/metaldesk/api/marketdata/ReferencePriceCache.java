package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * The last-known reference price of each metal, and the one before it, so that the panel can show
 * the direction and size of the most recent change (BRD FR-1.1).
 *
 * <p>A metal with a single observation has a latest price and <em>no previous one</em>: {@link
 * Observation#previous()} is empty, and callers report the change as absent. It is never reported
 * as a zero change, which would tell the customer the price is steady when it is merely unknown. An
 * observation with the same time as the latest is a repeat of it and is ignored, so polling a
 * source that has not moved cannot overwrite the previous price with the latest one.
 *
 * <p>The cache is in memory and per instance; each instance polls the source itself.
 */
@Component
public class ReferencePriceCache {

  private final ConcurrentMap<Metal, Observation> observations = new ConcurrentHashMap<>();

  /**
   * Records a new observation, keeping the one it replaces as the previous.
   *
   * @param price the observed price
   */
  public void record(ReferencePrice price) {
    observations.merge(
        price.metal(),
        new Observation(price, Optional.empty()),
        (held, incoming) ->
            held.latest().observedAt().equals(incoming.latest().observedAt())
                ? held
                : new Observation(incoming.latest(), Optional.of(held.latest())));
  }

  /**
   * Looks up the observations of one metal.
   *
   * @param metal the metal
   * @return its latest and previous prices, or empty if none has been observed
   */
  public Optional<Observation> find(Metal metal) {
    return Optional.ofNullable(observations.get(metal));
  }

  /**
   * Lists the observations of every metal seen so far, in the order of {@link Metal}.
   *
   * @return the observations; empty on a cold start
   */
  public List<Observation> all() {
    return List.of(Metal.values()).stream().flatMap(metal -> find(metal).stream()).toList();
  }

  /**
   * A metal's latest price and the one before it.
   *
   * @param latest the most recent observation
   * @param previous the observation before it, or empty if only one has been seen
   */
  public record Observation(ReferencePrice latest, Optional<ReferencePrice> previous) {}
}
