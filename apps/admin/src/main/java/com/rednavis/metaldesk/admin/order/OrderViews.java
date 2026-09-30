package com.rednavis.metaldesk.admin.order;

import com.rednavis.metaldesk.admin.order.dto.OrderDetailView;
import com.rednavis.metaldesk.admin.order.dto.OrderDetailView.HandoffContext;
import com.rednavis.metaldesk.admin.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.document.ShipmentDocument;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderLine;
import com.rednavis.metaldesk.share.domain.order.OrderStateMachine;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import java.util.ArrayList;
import java.util.List;

/** Builds the views of the order API from the domain order. */
public final class OrderViews {

  private OrderViews() {}

  /**
   * Builds the list row of an order.
   *
   * @param order the order
   * @return the summary
   */
  public static OrderSummaryView summary(Order order) {
    final OrderTotals totals = order.totals();
    return new OrderSummaryView(
        order.id().value(),
        order.number().format(),
        order.status(),
        order.customerId().value(),
        order.itemCount(),
        totals.grandTotal().amount().toPlainString(),
        totals.grandTotal().currency().code(),
        order.createdAt(),
        order.updatedAt());
  }

  /**
   * Builds the full view of an order.
   *
   * @param order the order
   * @param customer the customer record, or null if it is gone
   * @param shipment the shipment, or null if none was entered
   * @param handoff the handoff context, or null if the order is not awaiting a quote
   * @return the detail
   */
  public static OrderDetailView detail(
      Order order, CustomerDocument customer, ShipmentDocument shipment, HandoffContext handoff) {
    final OrderTotals totals = order.totals();
    final List<TransitionTrigger> actions =
        new ArrayList<>(OrderStateMachine.availableFrom(order.status()));
    actions.sort(null);
    return new OrderDetailView(
        summary(order),
        customer == null ? null : customer.name(),
        customer == null
            ? null
            : new OrderDetailView.ContactView(customer.email(), customer.phone()),
        destination(order.deliveryAddress()),
        order.lines().stream().map(OrderViews::line).toList(),
        totals.net().amount().toPlainString(),
        totals.tax().amount().toPlainString(),
        totals.delivery().amount().toPlainString(),
        order.quote().map(OrderViews::quote).orElse(null),
        shipment == null
            ? null
            : new OrderDetailView.ShipmentView(shipment.carrier(), shipment.trackingReference()),
        handoff,
        actions);
  }

  private static OrderDetailView.LineView line(OrderLine line) {
    return new OrderDetailView.LineView(
        line.productId().value(),
        line.productName(),
        line.quantity().value(),
        line.price().unitPrice().amount().toPlainString(),
        line.lineNet().amount().toPlainString(),
        line.lineTax().amount().toPlainString());
  }

  private static OrderDetailView.QuoteInfo quote(DeliveryQuote quote) {
    return new OrderDetailView.QuoteInfo(
        quote.tierId().value(),
        quote.cost().amount().toPlainString(),
        quote.transit().minDays(),
        quote.transit().maxDays(),
        quote.quotedAt());
  }

  private static String destination(Address address) {
    return address.street()
        + ", "
        + address.postalCode()
        + " "
        + address.city()
        + ", "
        + address.country().code();
  }
}
