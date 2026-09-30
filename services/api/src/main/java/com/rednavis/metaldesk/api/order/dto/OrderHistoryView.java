package com.rednavis.metaldesk.api.order.dto;

import java.util.List;

/**
 * The orders of the signed-in customer, newest first.
 *
 * @param orders the orders
 */
public record OrderHistoryView(List<OrderSummaryView> orders) {

  /** Copies the list, so the record cannot be changed through it. */
  public OrderHistoryView {
    orders = List.copyOf(orders);
  }
}
