package com.rednavis.metaldesk.api.marketdata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Polls the {@link MarketDataClient} on a short interval (BRD FR-1.1: illustratively every 20
 * seconds) and feeds the {@link ReferencePriceCache}.
 *
 * <p>A failed poll is logged and skipped, and the cache keeps the prices it has: a stale price feed
 * must degrade the panel, never take the service down. The interval is {@code
 * metaldesk.market-data.refresh.interval}; {@code metaldesk.market-data.refresh.enabled=false}
 * switches polling off, which the tests use to control the cache themselves.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "metaldesk.market-data.refresh.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MarketDataRefresher {

  private final MarketDataClient client;
  private final ReferencePriceCache cache;

  /**
   * Polls the source once and records what it returns.
   *
   * @return a signal that completes when the poll has, and never fails
   */
  public Mono<Void> refresh() {
    return client
        .fetchLatest()
        .doOnNext(cache::record)
        .then()
        .doOnError(failure -> log.warn("Market-data refresh failed; keeping known prices", failure))
        .onErrorResume(failure -> Mono.empty());
  }

  /** Runs {@link #refresh()} on the configured interval, starting immediately. */
  @Scheduled(fixedDelayString = "${metaldesk.market-data.refresh.interval:20s}")
  public void scheduledRefresh() {
    refresh().subscribe();
  }
}
