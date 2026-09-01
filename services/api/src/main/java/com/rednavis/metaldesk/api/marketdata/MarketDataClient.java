package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import reactor.core.publisher.Flux;

/**
 * The port onto the market-data source: what {@code services/api} needs from {@code
 * pricing-bridge}, and nothing about how it is reached.
 *
 * <p>Nothing in {@code services/api} opens a connection to a real feed (ADR-0002). Today the only
 * implementation is {@link FakeMarketDataClient}; when {@code pricing-bridge}'s read surface exists
 * (T-039) an HTTP-backed implementation replaces it behind this same interface.
 */
@FunctionalInterface
public interface MarketDataClient {

  /**
   * Fetches the most recent reference price of each supported metal.
   *
   * <p>A metal the source has no price for is simply absent; a failure is an error signal, which
   * the caller treats as "keep the prices already known".
   *
   * @return at most one price per metal
   */
  Flux<ReferencePrice> fetchLatest();
}
