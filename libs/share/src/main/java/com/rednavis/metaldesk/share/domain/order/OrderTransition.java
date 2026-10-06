package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * One legal edge of the order state machine: from a status, on a trigger, to a status.
 *
 * <p>It is also the natural audit value for a transition that happened, since it keeps the reason
 * beside the two statuses. An edge whose {@code from} and {@code to} are equal is legal: {@link
 * TransitionTrigger#PAYMENT_FAILED} leaves the order where it is.
 *
 * @param from the status the edge leaves, never null
 * @param trigger why the edge fires, never null
 * @param to the status the edge arrives at, never null
 */
public record OrderTransition(OrderStatus from, TransitionTrigger trigger, OrderStatus to) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if any field is null
   */
  public OrderTransition {
    if (from == null || trigger == null || to == null) {
      throw new ValidationException(
          "order-transition.field-missing", "Order transition requires from, trigger and to");
    }
  }
}
