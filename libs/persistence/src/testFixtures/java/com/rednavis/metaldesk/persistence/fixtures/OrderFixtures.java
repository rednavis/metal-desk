package com.rednavis.metaldesk.persistence.fixtures;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.fulfillment.TransitTime;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import com.rednavis.metaldesk.share.domain.pricing.SellablePrice;
import com.rednavis.metaldesk.share.domain.pricing.TaxRate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Small synthetic orders for the persistence tests, each with the awkward case a lossy mapping
 * would drop: a scale-sensitive amount, an optional field, a price or tax snapshot.
 */
public final class OrderFixtures {

  /** A fixed instant, in whole milliseconds because MongoDB stores dates to the millisecond. */
  public static final Instant NOW = Instant.parse("2026-09-28T10:15:30.123Z");

  private OrderFixtures() {}

  /** A sellable price with fixed provenance. */
  public static SellablePrice sellable(Money unit, PriceRule.Scope scope) {
    return new SellablePrice(
        unit,
        new PriceRule(Margin.of("5.5"), scope),
        new ReferencePrice(Metal.GOLD, Money.of("60.00", unit.currency()), NOW));
  }

  /** Two zero-rated gold bars. */
  public static OrderLine goldLine() {
    return OrderLine.of(
        new ProductId("gold-bar"),
        "Gold bar 1 oz",
        Quantity.of(2),
        sellable(
            CatalogFixtures.eur("1959.32"),
            new PriceRule.Scope.ForCategory(new CategoryId("cat-bars"))),
        TaxCategory.INVESTMENT_GRADE,
        TaxRate.ZERO);
  }

  /** Three standard-rated silver items at 19 percent. */
  public static OrderLine silverLine() {
    return OrderLine.of(
        new ProductId("silver-bar"),
        "Silver bar 100 g",
        Quantity.of(3),
        sellable(
            CatalogFixtures.eur("89.16"),
            new PriceRule.Scope.ForProduct(new ProductId("silver-bar"))),
        TaxCategory.STANDARD,
        TaxRate.of("19"));
  }

  /** A newly created order of the gold and silver lines. */
  public static Order newOrder(String id, OrderNumber number) {
    return Order.created(
        new OrderId(id),
        number,
        new CustomerId("cust-1"),
        AccountFixtures.deliveryAddress(),
        List.of(goldLine(), silverLine()),
        NOW);
  }

  /** A paid order with a delivery quote and a captured payment. */
  public static Order paidOrder(String id, OrderNumber number) {
    final Order created = newOrder(id, number);
    return new Order(
        created.id(),
        created.number(),
        created.customerId(),
        created.deliveryAddress(),
        created.lines(),
        Optional.of(
            new DeliveryQuote(
                new FulfillmentTierId("tier-1"),
                CatalogFixtures.eur("12.50"),
                new TransitTime(2, 4),
                NOW)),
        Optional.of(
            new PaymentRecord(
                "gateway",
                PaymentMethod.CARD,
                PaymentStatus.CAPTURED,
                new ProviderReference("pay_123"),
                CatalogFixtures.eur("4200.00"))),
        OrderStatus.PAID,
        NOW,
        NOW.plusSeconds(60));
  }

  /** An order number on a fixed day. */
  public static OrderNumber number(int sequence) {
    return new OrderNumber(LocalDate.of(2026, 9, 28), sequence);
  }
}
