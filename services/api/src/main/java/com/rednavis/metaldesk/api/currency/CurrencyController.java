package com.rednavis.metaldesk.api.currency;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The display currencies on offer (BRD FR-1.8). Public, like the catalog. */
@RestController
@RequestMapping("/api/currencies")
@RequiredArgsConstructor
public class CurrencyController {

  private final DisplayCurrencies currencies;

  /**
   * Lists the currencies prices can be shown in.
   *
   * @return the settlement currency, the options, and whether the rates are demo rates
   */
  @GetMapping
  public CurrencyOptionsView options() {
    return currencies.options();
  }
}
