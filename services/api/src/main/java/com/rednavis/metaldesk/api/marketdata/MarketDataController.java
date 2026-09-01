package com.rednavis.metaldesk.api.marketdata;

import com.rednavis.metaldesk.api.currency.DisplayCurrencies;
import com.rednavis.metaldesk.share.domain.money.Currency;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** The reference-price panel of BRD FR-1.1. Public: catalog reads need no sign-in. */
@RestController
@RequestMapping("/api/market-data")
@RequiredArgsConstructor
public class MarketDataController {

  private final MarketDataService service;
  private final DisplayCurrencies currencies;

  /**
   * Lists the latest reference price of each metal seen so far, with the change since the
   * observation before it.
   *
   * <p>On a cold start the list is empty, and a metal seen only once has no {@code change}.
   *
   * @param currency the currency to show them in, the settlement currency by default
   * @return the prices, in metal order
   */
  @GetMapping("/prices")
  public Flux<ReferencePriceView> prices(@RequestParam(required = false) String currency) {
    final Currency shown = currencies.resolve(currency);
    return service.prices().map(view -> currencies.reference(view, shown));
  }
}
