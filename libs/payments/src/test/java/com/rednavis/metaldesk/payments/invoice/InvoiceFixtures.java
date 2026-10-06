package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Synthetic orders and lines for the invoice tests. */
public final class InvoiceFixtures {

  /** The order id every fixture order carries. */
  public static final OrderId ORDER_ID = new OrderId("o-1");

  private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

  private InvoiceFixtures() {}

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
   * Builds two zero-rated gold bars at 1959.32 each: net 3918.64, tax 0.00.
   *
   * @return the line
   */
  public static OrderLine goldLine() {
    return line("gold-bar", "Gold bar 1 oz", 2, "1959.32", TaxCategory.INVESTMENT_GRADE, "0");
  }

  /**
   * Builds three standard-rated silver bars at 89.16 each: net 267.48, tax 50.82 at 19 percent.
   *
   * @return the line
   */
  public static OrderLine silverLine() {
    return line("silver-bar", "Silver bar 100 g", 3, "89.16", TaxCategory.STANDARD, "19");
  }

  /**
   * Builds a line.
   *
   * @param productId the product id text
   * @param name the product name
   * @param quantity how many units
   * @param unit the unit price in euro
   * @param category the tax category snapshotted on the line
   * @param ratePercent the tax rate in percent
   * @return the line
   */
  public static OrderLine line(
      String productId,
      String name,
      int quantity,
      String unit,
      TaxCategory category,
      String ratePercent) {
    final PriceRule rule =
        new PriceRule(Margin.of("5"), new PriceRule.Scope.ForCategory(new CategoryId("c-1")));
    final ReferencePrice reference = new ReferencePrice(Metal.GOLD, eur("60.00"), NOW);
    return OrderLine.of(
        new ProductId(productId),
        name,
        Quantity.of(quantity),
        new SellablePrice(eur(unit), rule, reference),
        category,
        TaxRate.of(ratePercent));
  }

  /**
   * Builds an order of the given lines, with a delivery quote of 25.00.
   *
   * @param lines the lines
   * @return the order
   */
  public static Order order(List<OrderLine> lines) {
    final Address address =
        new Address(
            AddressKind.DELIVERY, "1 Main Street", "Berlin", new Region("de"), "10115", null, null);
    final DeliveryQuote quote =
        new DeliveryQuote(
            new FulfillmentTierId("tier-1"), eur("25.00"), new TransitTime(1, 3), NOW);
    return new Order(
        ORDER_ID,
        new OrderNumber(LocalDate.of(2022, 2, 8), 4),
        new CustomerId("c-1"),
        address,
        lines,
        Optional.of(quote),
        Optional.empty(),
        OrderStatus.AWAITING_PAYMENT,
        NOW,
        NOW);
  }
}
