package com.rednavis.metaldesk.share.domain.order;

import static com.rednavis.metaldesk.share.domain.order.OrderFixtures.eur;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTotalsCalculatorTest {

  private static final Money DELIVERY = eur("25.00");

  /**
   * BR-5 by hand. Net: 2 x 1959.32 + 3 x 89.16 = 3918.64 + 267.48 = 4186.12. Tax: 0.00 on the
   * zero-rated gold + 19% of 267.48 = 50.82 on the silver. Total: 4186.12 + 50.82 + 25.00 delivery
   * = 4261.94.
   */
  @Test
  void twoLineExampleWithDeliveryMatchesBr5() {
    final OrderTotals totals =
        OrderTotalsCalculator.compute(
            List.of(OrderFixtures.goldLine(), OrderFixtures.silverLine()), DELIVERY);
    assertEquals(eur("4186.12"), totals.net());
    assertEquals(eur("50.82"), totals.tax());
    assertEquals(DELIVERY, totals.delivery());
    assertEquals(eur("4261.94"), totals.grandTotal());
  }

  @Test
  void zeroDeliveryLeavesNetPlusTax() {
    final OrderTotals totals =
        OrderTotalsCalculator.compute(
            List.of(OrderFixtures.silverLine()), Money.zero(Currency.EUR));
    assertEquals(eur("318.30"), totals.grandTotal());
  }

  @Test
  void linesInTwoCurrenciesAreRefused() {
    final OrderLine dollars =
        OrderLine.of(
            new ProductId("usd-bar"),
            "Bar in dollars",
            Quantity.of(1),
            OrderFixtures.sellable(Money.of("100.00", Currency.USD)),
            TaxCategory.STANDARD,
            TaxRate.of("19"));
    final List<OrderLine> mixed = List.of(OrderFixtures.silverLine(), dollars);
    assertEquals(
        "money.currency-mismatch",
        assertThrows(
                ValidationException.class, () -> OrderTotalsCalculator.compute(mixed, DELIVERY))
            .code());
  }

  @Test
  void deliveryInAnotherCurrencyThanTheLinesIsRefused() {
    final List<OrderLine> lines = List.of(OrderFixtures.silverLine());
    final Money dollars = Money.of(DELIVERY.amount(), Currency.USD);
    assertEquals(
        "money.currency-mismatch",
        assertThrows(ValidationException.class, () -> OrderTotalsCalculator.compute(lines, dollars))
            .code());
  }

  @Test
  void noLinesAndNoDeliveryAreRefused() {
    final List<OrderLine> lines = List.of(OrderFixtures.goldLine());
    assertEquals(
        "order-totals.no-lines",
        assertThrows(
                ValidationException.class, () -> OrderTotalsCalculator.compute(List.of(), DELIVERY))
            .code());
    assertEquals(
        "order-totals.delivery-missing",
        assertThrows(ValidationException.class, () -> OrderTotalsCalculator.compute(lines, null))
            .code());
  }

  @Test
  void totalsThatDoNotAddUpAreRefused() {
    assertEquals(
        "order-totals.inconsistent",
        assertThrows(
                ValidationException.class,
                () -> new OrderTotals(eur("10.00"), eur("1.00"), eur("2.00"), eur("99.00")))
            .code());
  }
}
