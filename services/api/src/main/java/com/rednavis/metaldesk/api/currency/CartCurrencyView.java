package com.rednavis.metaldesk.api.currency;

import com.rednavis.metaldesk.api.cart.dto.CartLineView;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.cart.dto.TotalsView;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Shows a cart in a display currency: every line converted, and the totals <em>rebuilt from the
 * converted lines</em>, so that what is shown always adds up (net is the sum of the converted line
 * nets, tax the sum of the converted line taxes, total their sum), even though a cent of rounding
 * may differ from converting the totals on their own.
 */
public final class CartCurrencyView {

  private CartCurrencyView() {}

  /**
   * Converts a cart.
   *
   * @param view the cart in the settlement currency
   * @param target the display currency
   * @param price converts one price to the display currency
   * @return the cart in the display currency
   */
  public static CartView convert(CartView view, Currency target, UnaryOperator<PriceView> price) {
    final List<CartLineView> lines = view.lines().stream().map(line -> line(line, price)).toList();
    return new CartView(
        view.cartId(),
        view.empty(),
        view.itemCount(),
        view.complete(),
        lines,
        view.totals() == null ? null : totals(lines, target));
  }

  private static CartLineView line(CartLineView line, UnaryOperator<PriceView> price) {
    return new CartLineView(
        line.productId(),
        line.name(),
        line.quantity(),
        line.maxQuantity(),
        line.pricingMode(),
        price.apply(line.unitPrice()),
        price.apply(line.lineNet()),
        line.taxRatePercent(),
        price.apply(line.lineTax()));
  }

  private static TotalsView totals(List<CartLineView> lines, Currency target) {
    final Money zero = Money.zero(target);
    final Money net =
        lines.stream()
            .filter(line -> line.lineNet() != null)
            .map(line -> Money.of(line.lineNet().amount(), target))
            .reduce(zero, Money::plus);
    final Money tax =
        lines.stream()
            .filter(line -> line.lineTax() != null)
            .map(line -> Money.of(line.lineTax().amount(), target))
            .reduce(zero, Money::plus);
    return new TotalsView(view(net), view(tax), view(net.plus(tax)));
  }

  private static PriceView view(Money money) {
    return new PriceView(money.amount().toPlainString(), money.currency().code());
  }
}
