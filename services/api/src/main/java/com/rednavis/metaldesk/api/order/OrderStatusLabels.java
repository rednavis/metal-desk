package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.share.domain.order.OrderStatus;

/**
 * The customer-facing wording of each order status (BRD FR-10.1: "a consistent status for every
 * order, regardless of which path it took").
 *
 * <p>The switch has no default branch, so a status added to the state machine is a compile error
 * here until it has a label. The manager-handoff state is an ordinary status like the others.
 */
public final class OrderStatusLabels {

  private OrderStatusLabels() {}

  /**
   * The label the customer sees for a status.
   *
   * @param status the status
   * @return the label
   */
  public static String of(OrderStatus status) {
    return switch (status) {
      case CREATED -> "Order started";
      case AWAITING_PAYMENT -> "Awaiting payment";
      case AWAITING_MANAGER_QUOTE -> "Awaiting your manager's quote";
      case PAID -> "Paid";
      case FULFILLING -> "Being prepared for shipping";
      case SHIPPED -> "Shipped";
      case DELIVERED -> "Delivered";
      case CANCELLED -> "Cancelled";
    };
  }

  /**
   * Whether carrier and tracking details can exist for a status: from shipping onward.
   *
   * @param status the status
   * @return true for {@code SHIPPED} and {@code DELIVERED}
   */
  public static boolean shipped(OrderStatus status) {
    return status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED;
  }
}
