package com.rednavis.metaldesk.share.domain.order;

/**
 * Why an order changes status: the cause of a transition, and the key the {@link
 * OrderStateMachine}'s table is indexed by, together with the current status.
 *
 * <p>A bare from-to table could not tell the roads into {@link OrderStatus#CANCELLED} apart, and
 * BRD FR-8.1 needs a support-actionable reason, so the trigger is part of the edge. Whoever fires a
 * trigger (checkout, payment, staff) passes one of these; who is <em>allowed</em> to fire it is
 * authorisation, not this type's concern.
 *
 * <p>Every trigger is legal from exactly one status. The names follow Architecture section 6 and
 * the task's list; two edges the diagram draws have no cause in that list, so {@link
 * #CHECKOUT_SUBMITTED} and {@link #FULFILLMENT_STARTED} are named here.
 */
public enum TransitionTrigger {

  /** The customer confirmed the order overview: {@code CREATED} to {@code AWAITING_PAYMENT}. */
  CHECKOUT_SUBMITTED,

  /** Funds were received: {@code AWAITING_PAYMENT} to {@code PAID}. */
  PAYMENT_CAPTURED,

  /**
   * One payment attempt failed. Per BRD FR-6.3 the customer returns to payment selection with their
   * data intact, so this does <em>not</em> change the status: {@code AWAITING_PAYMENT} stays {@code
   * AWAITING_PAYMENT}. It is a legal, recorded event rather than an error.
   */
  PAYMENT_FAILED,

  /**
   * Payment could not be completed and the order was given up, for example the customer stopped
   * retrying or the session expired: {@code AWAITING_PAYMENT} to {@code CANCELLED}. This is the
   * "payment failure" cause of cancellation in Architecture section 6, kept apart from {@link
   * #PAYMENT_FAILED} because FR-6.3 forbids cancelling on a single failed attempt.
   */
  PAYMENT_ABANDONED,

  /** The customer cancelled before paying: {@code AWAITING_PAYMENT} to {@code CANCELLED}. */
  CUSTOMER_CANCELLED,

  /**
   * The order exceeds a fulfillment-tier ceiling (BRD FR-5.3, BR-10): {@code AWAITING_PAYMENT} to
   * {@code AWAITING_MANAGER_QUOTE}.
   */
  TIER_EXCEEDED,

  /** Staff set the delivery terms: {@code AWAITING_MANAGER_QUOTE} to {@code AWAITING_PAYMENT}. */
  QUOTE_SET,

  /** The customer declined the staff quote: {@code AWAITING_MANAGER_QUOTE} to {@code CANCELLED}. */
  QUOTE_DECLINED,

  /** Picking and packing began: {@code PAID} to {@code FULFILLING}. */
  FULFILLMENT_STARTED,

  /** The order was handed to the carrier: {@code FULFILLING} to {@code SHIPPED}. */
  SHIPPED,

  /** The customer received the order: {@code SHIPPED} to {@code DELIVERED}. */
  DELIVERED
}
