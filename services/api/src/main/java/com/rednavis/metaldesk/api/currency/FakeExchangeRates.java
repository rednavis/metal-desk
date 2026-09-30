package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.share.domain.money.Currency;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Serves the demo rates from {@code metaldesk.fx.rates}. They are configuration, not a market, and
 * are reported as {@code FAKE}.
 */
@Component
@EnableConfigurationProperties(FxProperties.class)
public class FakeExchangeRates implements ExchangeRates {

  private final FxProperties properties;

  /**
   * Creates the provider.
   *
   * @param properties the settlement currency and the configured rates
   */
  public FakeExchangeRates(FxProperties properties) {
    this.properties = properties;
  }

  @Override
  public Currency settlement() {
    return properties.settlement();
  }

  @Override
  public String source() {
    return properties.source();
  }

  @Override
  public Optional<BigDecimal> rateTo(Currency target) {
    return target == properties.settlement()
        ? Optional.of(BigDecimal.ONE)
        : Optional.ofNullable(properties.rates().get(target));
  }
}
