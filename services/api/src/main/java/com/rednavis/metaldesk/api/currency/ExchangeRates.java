package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.share.domain.money.Currency;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Where display-currency rates come from: the port a real provider would implement.
 *
 * <p>The only implementation is {@link FakeExchangeRates}; a real one goes behind this interface
 * and its {@link #source()} stops saying {@code FAKE}.
 */
public interface ExchangeRates {

  /**
   * The currency orders are settled in.
   *
   * @return the settlement currency
   */
  Currency settlement();

  /**
   * What these rates are, for clients to disclose.
   *
   * @return for example {@code FAKE}
   */
  String source();

  /**
   * How many units of a currency one unit of the settlement currency is worth.
   *
   * @param target the display currency
   * @return the rate, one for the settlement currency, empty if the currency is not offered
   */
  Optional<BigDecimal> rateTo(Currency target);
}
