package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

  @Test
  void cancelledIsReachableByThreeDistinguishableTriggers() {
    final OrderTransition abandoned =
        OrderStateMachine.resolve(
            OrderStatus.AWAITING_PAYMENT, TransitionTrigger.PAYMENT_ABANDONED);
    final OrderTransition cancelled =
        OrderStateMachine.resolve(
            OrderStatus.AWAITING_PAYMENT, TransitionTrigger.CUSTOMER_CANCELLED);
    final OrderTransition declined =
        OrderStateMachine.resolve(
            OrderStatus.AWAITING_MANAGER_QUOTE, TransitionTrigger.QUOTE_DECLINED);
    assertEquals(OrderStatus.CANCELLED, abandoned.to());
    assertEquals(OrderStatus.CANCELLED, cancelled.to());
    assertEquals(OrderStatus.CANCELLED, declined.to());
    assertEquals(
        3,
        Stream.of(abandoned, cancelled, declined).map(OrderTransition::trigger).distinct().count());
  }

  @Test
  void quoteSetReturnsTheHandoffToAwaitingPayment() {
    assertEquals(
        OrderStatus.AWAITING_PAYMENT,
        OrderStateMachine.transition(
            OrderStatus.AWAITING_MANAGER_QUOTE, TransitionTrigger.QUOTE_SET));
  }

  @Test
  void failedPaymentAttemptLeavesOrderAwaitingPayment() {
    assertEquals(
        OrderStatus.AWAITING_PAYMENT,
        OrderStateMachine.transition(
            OrderStatus.AWAITING_PAYMENT, TransitionTrigger.PAYMENT_FAILED));
    assertTrue(
        OrderStateMachine.availableFrom(OrderStatus.AWAITING_PAYMENT)
            .contains(TransitionTrigger.PAYMENT_FAILED));
  }

  @Test
  void theOrdinaryPathReachesDelivered() {
    OrderStatus status = OrderStatus.CREATED;
    for (final TransitionTrigger trigger :
        new TransitionTrigger[] {
          TransitionTrigger.CHECKOUT_SUBMITTED,
          TransitionTrigger.PAYMENT_CAPTURED,
          TransitionTrigger.FULFILLMENT_STARTED,
          TransitionTrigger.SHIPPED,
          TransitionTrigger.DELIVERED
        }) {
      status = OrderStateMachine.transition(status, trigger);
    }
    assertEquals(OrderStatus.DELIVERED, status);
  }

  @Test
  void handoffPathIsStatusOfItsOwnAndRejoinsPayment() {
    final OrderStatus quoted =
        OrderStateMachine.transition(OrderStatus.AWAITING_PAYMENT, TransitionTrigger.TIER_EXCEEDED);
    assertEquals(OrderStatus.AWAITING_MANAGER_QUOTE, quoted);
    assertEquals(
        OrderStatus.PAID,
        OrderStateMachine.transition(
            OrderStateMachine.transition(quoted, TransitionTrigger.QUOTE_SET),
            TransitionTrigger.PAYMENT_CAPTURED));
  }

  @Test
  void illegalTransitionIsCaughtAsConflictNamingWhatWasAllowed() {
    // Catching the parent kind is what services/api does to map it to one HTTP class.
    final ConflictException thrown =
        assertThrows(
            ConflictException.class,
            () ->
                OrderStateMachine.transition(
                    OrderStatus.PAID, TransitionTrigger.CUSTOMER_CANCELLED));
    assertEquals("order.illegal-transition", thrown.code());
    assertTrue(thrown.getMessage().contains("FULFILLMENT_STARTED"));
  }

  @Test
  void nullArgumentsAreRefused() {
    assertThrows(
        ValidationException.class,
        () -> OrderStateMachine.transition(null, TransitionTrigger.SHIPPED));
    assertThrows(
        ValidationException.class, () -> OrderStateMachine.canTransition(OrderStatus.PAID, null));
    assertThrows(ValidationException.class, () -> OrderStateMachine.availableFrom(null));
  }
}
