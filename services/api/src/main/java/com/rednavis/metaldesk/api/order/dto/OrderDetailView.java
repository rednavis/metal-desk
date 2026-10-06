package com.rednavis.metaldesk.api.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * The drill-down of one order (BRD FR-10.1).
 *
 * <p>{@code shipment} is absent, not empty, until the order has shipped, so a client cannot render
 * an empty tracking link.
 *
 * @param orderNumber the order number
 * @param createdAt when the order was created
 * @param itemCount the number of items across all lines
 * @param status the status, by name
 * @param statusLabel the status as the customer reads it
 * @param lines the lines as snapshotted
 * @param deliveryAddress where it is delivered
 * @param totals the amounts, as snapshotted
 * @param paymentMethod the payment method, once there is a payment
 * @param paymentStatus where that payment stands, by name; a {@code PENDING} invoice means the
 *     invoice was issued and awaits payment
 * @param shipment carrier and tracking, from shipping onward
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderDetailView(
    String orderNumber,
    Instant createdAt,
    int itemCount,
    String status,
    String statusLabel,
    List<OrderLineView> lines,
    OrderAddressView deliveryAddress,
    OrderTotalsView totals,
    String paymentMethod,
    String paymentStatus,
    ShipmentView shipment) {

  /** Copies the list, so the record cannot be changed through it. */
  public OrderDetailView {
    lines = List.copyOf(lines);
  }
}
