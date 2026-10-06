package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Optional;

/**
 * The one input checkout accepts (BRD FR-3.2): a basket of priced lines, whether it came from the
 * persistent cart or from a buy-now that skipped the cart entirely. Checkout never needs to know
 * which.
 *
 * <p>The lines are {@code OrderLine}s as they would be ordered <em>now</em>; they are a preview,
 * not a commitment, and price finality (BRD BR-2) only applies once an order snapshots them.
 * Anything that could not be priced is listed in {@code unpriced} and stops the basket being
 * {@linkplain #checkoutable() checkoutable}.
 *
 * @param source where the basket came from
 * @param cart the cart it came from, empty for a buy-now
 * @param lines the priced lines
 * @param unpriced the products in it that have no sellable price at the moment
 */
public record CheckoutBasket(
    Source source, Optional<CartId> cart, List<OrderLine> lines, List<ProductId> unpriced) {

  /** Where a basket came from. */
  public enum Source {
    /** The persistent cart. */
    CART,
    /** A buy-now of a single unit, which never touched the cart. */
    BUY_NOW
  }

  /**
   * Validates the basket and copies its lists.
   *
   * @throws ValidationException if a field is null
   */
  public CheckoutBasket {
    if (source == null || cart == null || lines == null || unpriced == null) {
      throw new ValidationException(
          "checkout-basket.field-missing",
          "A basket needs a source, cart, lines and unpriced list");
    }
    lines = List.copyOf(lines);
    unpriced = List.copyOf(unpriced);
  }

  /**
   * Builds a basket from priced cart lines: those with a sellable price become order lines, the
   * rest are listed as unpriced.
   *
   * @param source where the lines came from
   * @param cart the cart they came from, empty for a buy-now or no cart
   * @param lines the lines as priced now
   * @return the basket
   */
  public static CheckoutBasket of(Source source, Optional<CartId> cart, List<PricedLine> lines) {
    return new CheckoutBasket(
        source,
        cart,
        lines.stream().flatMap(line -> line.priced().stream()).toList(),
        lines.stream()
            .filter(line -> line.priced().isEmpty())
            .map(line -> line.line().productId())
            .toList());
  }

  /**
   * Computes the totals by BRD BR-5, with no delivery yet.
   *
   * @return net, tax and grand total of the priced lines; empty if there are none
   */
  public Optional<OrderTotals> totals() {
    return lines.isEmpty()
        ? Optional.empty()
        : Optional.of(
            OrderTotalsCalculator.compute(lines, Money.zero(lines.get(0).lineNet().currency())));
  }

  /**
   * Tells whether checkout may proceed with this basket.
   *
   * @return {@code true} if it has lines and every one of them is priced
   */
  public boolean checkoutable() {
    return !lines.isEmpty() && unpriced.isEmpty();
  }
}
