package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.cart.dto.CartLineView;
import com.rednavis.metaldesk.api.cart.dto.CartView;
import com.rednavis.metaldesk.api.cart.dto.TotalsView;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.share.domain.catalog.PricingMode;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import java.util.List;
import java.util.Optional;

/** Turns priced cart lines into the views the storefront gets. No domain type leaves here. */
public final class CartViews {

  private CartViews() {}

  /**
   * Builds the view of a set of lines.
   *
   * @param cartId the cart's reference, empty for a buy-now or a request with no cart
   * @param lines the lines as priced now
   * @param basket the same lines as a basket, for the totals
   * @return the view
   */
  public static CartView of(
      Optional<CartId> cartId, List<PricedLine> lines, CheckoutBasket basket) {
    return new CartView(
        cartId.map(CartId::value).orElse(null),
        lines.isEmpty(),
        lines.stream().mapToInt(line -> line.line().quantity().value()).sum(),
        basket.unpriced().isEmpty(),
        lines.stream().map(CartViews::line).toList(),
        basket.totals().map(CartViews::totals).orElse(null));
  }

  private static CartLineView line(PricedLine priced) {
    final Optional<OrderLine> order = priced.priced();
    return new CartLineView(
        priced.line().productId().value(),
        priced.name(),
        priced.line().quantity().value(),
        Quantity.MAX,
        order.isPresent() ? PricingMode.FIXED : PricingMode.ON_REQUEST,
        order.map(line -> price(line.price().unitPrice())).orElse(null),
        order.map(line -> price(line.lineNet())).orElse(null),
        order.map(line -> line.tax().rate().percent().toPlainString()).orElse(null),
        order.map(line -> price(line.lineTax())).orElse(null));
  }

  private static TotalsView totals(OrderTotals totals) {
    return new TotalsView(price(totals.net()), price(totals.tax()), price(totals.grandTotal()));
  }

  private static PriceView price(Money money) {
    return new PriceView(money.amount().toPlainString(), money.currency().code());
  }
}
