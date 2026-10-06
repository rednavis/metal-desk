package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The exhaustive check of the transition table against an independently written expectation. */
class OrderStateMachineTableTest {

  /**
   * The expected table, written out separately from the implementation so a typo in either shows
   * up: for each status, the status each accepted trigger leads to.
   */
  private static final Map<OrderStatus, Map<TransitionTrigger, OrderStatus>> EXPECTED =
      Map.of(
          OrderStatus.CREATED,
          Map.of(TransitionTrigger.CHECKOUT_SUBMITTED, OrderStatus.AWAITING_PAYMENT),
          OrderStatus.AWAITING_PAYMENT,
          Map.of(
              TransitionTrigger.PAYMENT_CAPTURED, OrderStatus.PAID,
              TransitionTrigger.PAYMENT_FAILED, OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.PAYMENT_ABANDONED, OrderStatus.CANCELLED,
              TransitionTrigger.CUSTOMER_CANCELLED, OrderStatus.CANCELLED,
              TransitionTrigger.TIER_EXCEEDED, OrderStatus.AWAITING_MANAGER_QUOTE),
          OrderStatus.AWAITING_MANAGER_QUOTE,
          Map.of(
              TransitionTrigger.QUOTE_SET, OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.QUOTE_DECLINED, OrderStatus.CANCELLED),
          OrderStatus.PAID,
          Map.of(TransitionTrigger.FULFILLMENT_STARTED, OrderStatus.FULFILLING),
          OrderStatus.FULFILLING,
          Map.of(TransitionTrigger.SHIPPED, OrderStatus.SHIPPED),
          OrderStatus.SHIPPED,
          Map.of(TransitionTrigger.DELIVERED, OrderStatus.DELIVERED),
          OrderStatus.DELIVERED,
          Map.of(),
          OrderStatus.CANCELLED,
          Map.of());

  private static Stream<Arguments> everyStatusAndTriggerPair() {
    return Arrays.stream(OrderStatus.values())
        .flatMap(
            status ->
                Arrays.stream(TransitionTrigger.values())
                    .map(trigger -> Arguments.of(status, trigger)));
  }

  @Test
  void pairTestCoversEveryCombination() {
    assertEquals(
        OrderStatus.values().length * TransitionTrigger.values().length,
        everyStatusAndTriggerPair().count());
  }

  @ParameterizedTest
  @MethodSource("everyStatusAndTriggerPair")
  void everyPairIsLegalEdgeOrThrows(OrderStatus status, TransitionTrigger trigger) {
    final OrderStatus expectedTarget = EXPECTED.get(status).get(trigger);
    assertEquals(expectedTarget != null, OrderStateMachine.canTransition(status, trigger));
    if (expectedTarget == null) {
      final IllegalTransitionException thrown =
          assertThrows(
              IllegalTransitionException.class,
              () -> OrderStateMachine.transition(status, trigger));
      assertEquals(status, thrown.from());
      assertEquals(trigger, thrown.trigger());
    } else {
      assertEquals(expectedTarget, OrderStateMachine.transition(status, trigger));
    }
  }

  @Test
  void legalEdgesAreExactlyTheExpectedOnes() {
    final long expectedCount = EXPECTED.values().stream().mapToLong(Map::size).sum();
    assertEquals(expectedCount, OrderStateMachine.transitions().size());
    for (final OrderTransition edge : OrderStateMachine.transitions()) {
      assertEquals(EXPECTED.get(edge.from()).get(edge.trigger()), edge.to());
    }
  }

  @Test
  void terminalStatusesAcceptNothingAndOthersAcceptSomething() {
    for (final OrderStatus status : OrderStatus.values()) {
      assertEquals(status.isTerminal(), OrderStateMachine.availableFrom(status).isEmpty());
    }
    assertTrue(OrderStateMachine.availableFrom(OrderStatus.DELIVERED).isEmpty());
    assertTrue(OrderStateMachine.availableFrom(OrderStatus.CANCELLED).isEmpty());
  }
}
