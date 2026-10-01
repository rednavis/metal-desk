package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.api.order.dto.OrderAddressView;
import com.rednavis.metaldesk.api.order.dto.OrderDetailView;
import com.rednavis.metaldesk.api.order.dto.OrderLineView;
import com.rednavis.metaldesk.api.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.api.order.dto.OrderTotalsView;
import com.rednavis.metaldesk.api.order.dto.ShipmentView;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import java.util.Optional;

/**
 * Builds the order views from the order alone.
 *
 * <p>Everything here reads the order's own snapshotted lines and quote, never a product: what an
 * order cost is what it cost when it was created (BRD BR-2), so a price change afterwards cannot
 * move a total a customer has already seen.
 */
public final class OrderViews {

  private OrderViews() {}

  /**
   * The history row of an order.
   *
   * @param order the order
   * @return the row
   */
  public static OrderSummaryView summary(Order order) {
    return new OrderSummaryView(
        order.number().format(),
        order.createdAt(),
        order.itemCount(),
        price(order.totals().grandTotal()),
        order.status().name(),
        OrderStatusLabels.of(order.status()));
  }

  /**
   * The drill-down of an order.
   *
   * @param order the order
   * @param shipment the shipment, which is used only from shipping onward
   * @return the detail
   */
  public static OrderDetailView detail(Order order, Optional<ShipmentDetails> shipment) {
    final OrderTotals totals = order.totals();
    return new OrderDetailView(
        order.number().format(),
        order.createdAt(),
        order.itemCount(),
        order.status().name(),
        OrderStatusLabels.of(order.status()),
        order.lines().stream().map(OrderViews::line).toList(),
        address(order.deliveryAddress()),
        new OrderTotalsView(
            price(totals.net()),
            price(totals.tax()),
            price(totals.delivery()),
            price(totals.grandTotal())),
        order.payment().map(payment -> payment.method().name()).orElse(null),
        order.payment().map(payment -> payment.status().name()).orElse(null),
        shipment
            .filter(details -> OrderStatusLabels.shipped(order.status()))
            .map(details -> new ShipmentView(details.carrier(), details.trackingReference()))
            .orElse(null));
  }

  /**
   * A money amount as the wire carries it.
   *
   * @param money the amount
   * @return the view
   */
  public static PriceView price(Money money) {
    return new PriceView(money.amount().toPlainString(), money.currency().code());
  }

  private static OrderLineView line(OrderLine line) {
    return new OrderLineView(
        line.productName(),
        line.quantity().value(),
        price(line.price().unitPrice()),
        price(line.lineNet()),
        line.tax().rate().percent().toPlainString(),
        price(line.lineTax()));
  }

  private static OrderAddressView address(Address address) {
    return new OrderAddressView(
        address.street(), address.postalCode(), address.city(), address.country().code());
  }
}
