package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.error.ConflictException;
import java.io.Serial;
import java.util.Set;

/**
 * Signals that an order in some status cannot accept a trigger (BRD FR-8.1: support-actionable).
 *
 * <p>It is a {@link ConflictException}: the request is well-formed but the order's current state
 * forbids it, so every service maps it to the same HTTP class as any other state conflict. The
 * exception carries the status and the trigger, so a caller can log the reason without parsing the
 * message, and the message names the triggers that would have been accepted.
 */
public final class IllegalTransitionException extends ConflictException {

  @Serial private static final long serialVersionUID = 1L;

  private final OrderStatus fromStatus;
  private final TransitionTrigger firedTrigger;

  /**
   * Creates the failure.
   *
   * @param from the status the order is in
   * @param trigger the trigger that was refused
   * @param allowed the triggers the status would have accepted, possibly empty
   */
  public IllegalTransitionException(
      OrderStatus from, TransitionTrigger trigger, Set<TransitionTrigger> allowed) {
    super(
        "order.illegal-transition",
        "An order in " + from + " cannot accept " + trigger + "; allowed here: " + allowed);
    this.fromStatus = from;
    this.firedTrigger = trigger;
  }

  /**
   * Returns the status the order was in.
   *
   * @return the status
   */
  public OrderStatus from() {
    return fromStatus;
  }

  /**
   * Returns the trigger that was refused.
   *
   * @return the trigger
   */
  public TransitionTrigger trigger() {
    return firedTrigger;
  }
}
