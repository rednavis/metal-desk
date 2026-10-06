package com.rednavis.metaldesk.api.currency;

import java.util.List;

/**
 * The currencies prices can be shown in, and how they are converted.
 *
 * @param settlement the currency orders are priced and charged in; checkout and order history are
 *     always in this one
 * @param rateSource what the rates are: {@code FAKE} means demo rates, to be disclosed as such
 * @param options the display currencies, the settlement currency first
 */
public record CurrencyOptionsView(
    String settlement, String rateSource, List<CurrencyOption> options) {

  /** Copies the list, so the view cannot be changed through it. */
  public CurrencyOptionsView {
    options = List.copyOf(options);
  }

  /**
   * One display currency.
   *
   * @param code the currency code
   * @param perSettlementUnit how many units of it one unit of the settlement currency is worth
   */
  public record CurrencyOption(String code, String perSettlementUnit) {}
}
