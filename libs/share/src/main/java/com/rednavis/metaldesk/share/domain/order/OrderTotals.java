package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The result of BRD BR-5: {@code order_total = sum(unit_price x quantity) + tax + delivery_cost}.
 *
 * <p>There is one delivery component and no insurance component: insurance is folded into delivery
 * cost (BRD BR-7, FR-5.4), so there is no place to add one. The canonical constructor refuses a
 * grand total that is not the sum of the other three, so an instance cannot claim a total its parts
 * do not produce.
 *
 * @param net the sum of the line nets, before tax and delivery
 * @param tax the sum of the line taxes
 * @param delivery the delivery cost, insurance included
 * @param grandTotal net plus tax plus delivery
 */
public record OrderTotals(Money net, Money tax, Money delivery, Money grandTotal) {

  /**
   * Validates the fields and that the grand total is their sum.
   *
   * @throws ValidationException if a field is null, the currencies differ, or the grand total is
   *     not net plus tax plus delivery
   */
  public OrderTotals {
    if (net == null || tax == null || delivery == null || grandTotal == null) {
      throw new ValidationException(
          "order-totals.field-missing",
          "Order totals require net, tax, delivery and a grand total");
    }
    if (!grandTotal.equals(net.plus(tax).plus(delivery))) {
      throw new ValidationException(
          "order-totals.inconsistent", "Grand total is not net plus tax plus delivery");
    }
  }
}
