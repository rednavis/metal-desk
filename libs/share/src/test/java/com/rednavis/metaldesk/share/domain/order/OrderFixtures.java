package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Small synthetic order objects shared by the order tests. */
public final class OrderFixtures {

  /** When the fixture orders were created. */
  public static final Instant CREATED_AT = Instant.parse("2026-09-28T10:15:30Z");

  private OrderFixtures() {}

  /**
   * Builds an amount in euro.
   *
   * @param amount the decimal text
   * @return the amount
   */
  public static Money eur(String amount) {
    return Money.of(amount, Currency.EUR);
  }

  /**
   * Builds a sellable price with fixed provenance.
   *
   * @param unit the unit price
   * @return the price
   */
  public static SellablePrice sellable(Money unit) {
    final PriceRule rule =
        new PriceRule(Margin.of("5"), new PriceRule.Scope.ForCategory(new CategoryId("c-1")));
    final ReferencePrice reference =
        new ReferencePrice(Metal.GOLD, Money.of("60.00", unit.currency()), CREATED_AT);
    return new SellablePrice(unit, rule, reference);
  }

  /**
   * Builds a line of two zero-rated gold bars at 1959.32 each: net 3918.64, tax 0.00.
   *
   * @return the line
   */
  public static OrderLine goldLine() {
    return OrderLine.of(
        new ProductId("gold-bar"),
        "Gold bar 1 oz",
        Quantity.of(2),
        sellable(eur("1959.32")),
        TaxCategory.INVESTMENT_GRADE,
        TaxRate.ZERO);
  }

  /**
   * Builds a line of three standard-rated silver items at 89.16 each: net 267.48, tax 50.82 at 19%.
   *
   * @return the line
   */
  public static OrderLine silverLine() {
    return OrderLine.of(
        new ProductId("silver-bar"),
        "Silver bar 100 g",
        Quantity.of(3),
        sellable(eur("89.16")),
        TaxCategory.STANDARD,
        TaxRate.of("19"));
  }

  /**
   * Builds a delivery address.
   *
   * @return the address
   */
  public static Address deliveryAddress() {
    return new Address(
        AddressKind.DELIVERY, "1 Main Street", "Berlin", new Region("de"), "10115", null, null);
  }

  /**
   * Builds a newly created order of the given lines.
   *
   * @param lines the lines
   * @return the order
   */
  public static Order createdOrder(List<OrderLine> lines) {
    return Order.created(
        new OrderId("o-1"),
        new OrderNumber(LocalDate.of(2022, 2, 8), 4),
        new CustomerId("c-1"),
        deliveryAddress(),
        lines,
        CREATED_AT);
  }
}
