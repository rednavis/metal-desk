package com.rednavis.metaldesk.share.domain.order;

/**
 * Where an order is in its life (Architecture section 6), one of the eight states of the order
 * state machine.
 *
 * <p>This is the set of states. Which edges between them are legal, and what triggers each, is the
 * {@link OrderStateMachine}; nothing on {@link Order} moves it between states, so that table stays
 * in one place. {@link #AWAITING_MANAGER_QUOTE} is a state in its own right, not a flag, so order
 * history (BRD FR-10.1) shows a consistent status for every order whichever path it took.
 */
public enum OrderStatus {

  /** The order exists but checkout has not yet asked for payment. */
  CREATED,

  /** Waiting for the customer to pay. */
  AWAITING_PAYMENT,

  /** Exceeded a fulfillment-tier ceiling; waiting for staff to set the delivery terms (BR-10). */
  AWAITING_MANAGER_QUOTE,

  /** Payment has been received. */
  PAID,

  /** Being picked and packed. */
  FULFILLING,

  /** Handed to the carrier. */
  SHIPPED,

  /** Received by the customer. */
  DELIVERED,

  /** Abandoned before it was paid. */
  CANCELLED;

  /**
   * Tells whether the order can go no further: it is {@link #DELIVERED} or {@link #CANCELLED}. A
   * terminal status accepts no trigger in the {@link OrderStateMachine}.
   *
   * @return {@code true} for {@code DELIVERED} and {@code CANCELLED}
   */
  public boolean isTerminal() {
    return this == DELIVERED || this == CANCELLED;
  }
}
