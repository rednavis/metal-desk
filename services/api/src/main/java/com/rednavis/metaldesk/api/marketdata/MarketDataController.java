package com.rednavis.metaldesk.api.marketdata;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** The reference-price panel of BRD FR-1.1. Public: catalog reads need no sign-in. */
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

  private final MarketDataService service;

  /**
   * Lists the latest reference price of each metal seen so far, with the change since the
   * observation before it.
   *
   * <p>On a cold start the list is empty, and a metal seen only once has no {@code change}.
   *
   * @return the prices, in metal order
   */
  @GetMapping("/prices")
  public Flux<ReferencePriceView> prices() {
    return service.prices();
  }
}
