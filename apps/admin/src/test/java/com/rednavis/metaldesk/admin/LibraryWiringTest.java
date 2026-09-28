package com.rednavis.metaldesk.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rednavis.metaldesk.share.domain.order.OrderStateMachine;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import org.junit.jupiter.api.Test;

/**
 * The Phase 2 exit gate for {@code apps/admin}: it compiles against, and really uses, a type from
 * the library it declares. Staff set a handoff order's terms in the admin, which moves it back to
 * awaiting payment (Architecture section 6), so the type is the state machine that owns that move.
 */
class LibraryWiringTest {

  @Test
  void usesTheSharedOrderStateMachine() {
    assertEquals(
        OrderStatus.AWAITING_PAYMENT,
        OrderStateMachine.transition(
            OrderStatus.AWAITING_MANAGER_QUOTE, TransitionTrigger.QUOTE_SET));
  }
}
