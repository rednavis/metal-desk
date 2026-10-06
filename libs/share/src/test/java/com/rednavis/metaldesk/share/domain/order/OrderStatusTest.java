package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OrderStatusTest {

  @Test
  void declaresExactlyTheEightStatesOfArchitectureSection6() {
    final Set<String> names =
        Arrays.stream(OrderStatus.values()).map(Enum::name).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "CREATED",
            "AWAITING_PAYMENT",
            "AWAITING_MANAGER_QUOTE",
            "PAID",
            "FULFILLING",
            "SHIPPED",
            "DELIVERED",
            "CANCELLED"),
        names);
  }

  @Test
  void managerQuoteIsStatusNotFlag() {
    assertEquals(OrderStatus.AWAITING_MANAGER_QUOTE, OrderStatus.valueOf("AWAITING_MANAGER_QUOTE"));
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      names = {"DELIVERED", "CANCELLED"})
  void deliveredAndCancelledAreTerminal(OrderStatus status) {
    assertTrue(status.isTerminal());
  }

  @ParameterizedTest
  @EnumSource(
      value = OrderStatus.class,
      mode = EnumSource.Mode.EXCLUDE,
      names = {"DELIVERED", "CANCELLED"})
  void everyOtherStatusIsNotTerminal(OrderStatus status) {
    assertFalse(status.isTerminal());
  }
}
