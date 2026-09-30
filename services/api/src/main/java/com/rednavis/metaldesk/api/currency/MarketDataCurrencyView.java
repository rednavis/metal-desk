package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.api.marketdata.ReferencePriceView;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.math.BigDecimal;

/**
 * Shows a reference price in a display currency (BRD FR-1.8): the price per gram and the size of
 * its last change are converted with the same rate, so the change still reads as a part of the
 * price. The direction and the percentage are facts about the market and are left alone.
 */
public final class MarketDataCurrencyView {

  private MarketDataCurrencyView() {}

  /**
   * Converts one reference price.
   *
   * @param view the price in the settlement currency
   * @param target the display currency
   * @param rate how many units of the display currency one settlement unit is worth
   * @return the price in the display currency
   */
  public static ReferencePriceView convert(
      ReferencePriceView view, Currency target, BigDecimal rate) {
    final ReferencePriceView.ChangeView change = view.change();
    return new ReferencePriceView(
        view.metal(),
        money(view.pricePerGram(), target, rate),
        target.code(),
        view.observedAt(),
        change == null
            ? null
            : new ReferencePriceView.ChangeView(
                change.direction(), money(change.amount(), target, rate), change.percent()));
  }

  private static String money(String amount, Currency target, BigDecimal rate) {
    return Money.of(new BigDecimal(amount).multiply(rate), target).amount().toPlainString();
  }
}
