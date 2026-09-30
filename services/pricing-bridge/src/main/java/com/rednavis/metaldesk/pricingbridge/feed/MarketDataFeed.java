package com.rednavis.metaldesk.pricingbridge.feed;

import reactor.core.publisher.Flux;

/**
 * A source of reference-price observations: the port the subscription reads from.
 *
 * <p>The contract is a <em>stream</em>, not a poll. {@link #ticks()} is cold: every subscription
 * opens a fresh connection and the flux is expected to stay open indefinitely. A flux that
 * completes or errors means the connection was lost, and the subscription manager will resubscribe
 * with backoff, so an implementation must not retry internally.
 *
 * <p>A feed may emit faster than its consumer wants and is not required to honour demand; the
 * consumer applies the backpressure policy (see {@code SubscriptionManager}). An implementation
 * must therefore never buffer unboundedly on its own behalf either.
 */
@FunctionalInterface
public interface MarketDataFeed {

  /**
   * Opens a connection to the feed.
   *
   * @return the ticks, in observation order; never completes normally in a healthy feed
   */
  Flux<ReferencePriceTick> ticks();
}
