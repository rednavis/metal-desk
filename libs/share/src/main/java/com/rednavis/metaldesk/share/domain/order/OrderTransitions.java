package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.domain.fulfillment.DeliveryQuote;
import com.rednavis.metaldesk.share.domain.payment.PaymentRecord;
import java.time.Instant;
import java.util.Optional;

/**
 * Builds the next version of an {@link Order}, since {@code Order} deliberately has no method that
 * changes its status.
 *
 * <p>A status only ever changes through {@link OrderStateMachine#transition}, with a trigger: an
 * illegal one is refused there, and nothing in checkout assigns a status directly.
 */
public final class OrderTransitions {

  private OrderTransitions() {}

  /**
   * Fires a trigger on an order.
   *
   * @param order the order
   * @param trigger what happened
   * @param now when
   * @return the order in the status the state machine says follows, updated now
   * @throws com.rednavis.metaldesk.share.domain.order.IllegalTransitionException if the trigger is
   *     not legal from the order's status
   */
  public static Order advance(Order order, TransitionTrigger trigger, Instant now) {
    return new Order(
        order.id(),
        order.number(),
        order.customerId(),
        order.deliveryAddress(),
        order.lines(),
        order.quote(),
        order.payment(),
        OrderStateMachine.transition(order.status(), trigger),
        order.createdAt(),
        now);
  }

  /**
   * Attaches the delivery quote.
   *
   * @param order the order
   * @param quote the quote it was priced with
   * @return the order with the quote
   */
  public static Order withQuote(Order order, DeliveryQuote quote) {
    return new Order(
        order.id(),
        order.number(),
        order.customerId(),
        order.deliveryAddress(),
        order.lines(),
        Optional.of(quote),
        order.payment(),
        order.status(),
        order.createdAt(),
        order.updatedAt());
  }

  /**
   * Records a payment attempt's record on the order, without changing its status.
   *
   * @param order the order
   * @param record the payment record
   * @param now when
   * @return the order with the record
   */
  public static Order withPayment(Order order, PaymentRecord record, Instant now) {
    return new Order(
        order.id(),
        order.number(),
        order.customerId(),
        order.deliveryAddress(),
        order.lines(),
        order.quote(),
        Optional.of(record),
        order.status(),
        order.createdAt(),
        now);
  }
}
