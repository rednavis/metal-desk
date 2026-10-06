package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;

/**
 * Computes an order's totals exactly as BRD BR-5 states them: {@code order_total = sum(unit_price x
 * quantity) + tax + delivery_cost}.
 *
 * <p>A pure function of the lines it is given: the net is the sum of the line nets, the tax is the
 * sum of the line taxes as snapshotted when the order was placed (never recomputed from a live
 * rate), and the grand total is net plus tax plus delivery. Every sum goes through {@link
 * Money#plus}, so a mixed-currency order throws instead of producing a plausible wrong number.
 * Delivery is a single {@code Money}; insurance is folded into it (BRD BR-7).
 */
public final class OrderTotalsCalculator {

  private OrderTotalsCalculator() {}

  /**
   * Computes the totals of a set of lines and a delivery cost.
   *
   * @param lines the order lines, never empty
   * @param deliveryCost the delivery cost, insurance included; zero when none is quoted yet
   * @return the net, tax, delivery and grand total
   * @throws ValidationException if the lines are null, empty or hold a null, the delivery cost is
   *     null, or any amount is in a different currency from the delivery cost
   */
  public static OrderTotals compute(List<OrderLine> lines, Money deliveryCost) {
    if (lines == null || lines.isEmpty() || lines.stream().anyMatch(line -> line == null)) {
      throw new ValidationException(
          "order-totals.no-lines", "Order totals need at least one line and no null line");
    }
    if (deliveryCost == null) {
      throw new ValidationException(
          "order-totals.delivery-missing", "Delivery cost must not be null");
    }
    final Money zero = Money.zero(deliveryCost.currency());
    final Money net = lines.stream().map(OrderLine::lineNet).reduce(zero, Money::plus);
    final Money tax = lines.stream().map(OrderLine::lineTax).reduce(zero, Money::plus);
    return new OrderTotals(net, tax, deliveryCost, net.plus(tax).plus(deliveryCost));
  }
}
