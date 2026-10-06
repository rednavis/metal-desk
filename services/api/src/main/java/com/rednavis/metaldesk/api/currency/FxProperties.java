package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.share.domain.money.Currency;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of display-currency conversion, under {@code metaldesk.fx}.
 *
 * @param settlement the currency orders are priced and charged in
 * @param source what the rates are, reported to clients so they can say so; {@code FAKE} for the
 *     configured demo rates
 * @param rates how many units of each display currency one unit of the settlement currency is
 *     worth; the settlement currency itself is always 1 and need not be listed
 */
@ConfigurationProperties("metaldesk.fx")
public record FxProperties(
    @DefaultValue("EUR") Currency settlement,
    @DefaultValue("FAKE") String source,
    Map<Currency, BigDecimal> rates) {

  /** Copies the rates; an absent map means only the settlement currency is offered. */
  public FxProperties {
    rates = rates == null ? Map.of() : Map.copyOf(rates);
  }
}
