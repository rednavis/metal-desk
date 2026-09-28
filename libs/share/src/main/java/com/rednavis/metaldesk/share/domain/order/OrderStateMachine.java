package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The order state machine of Architecture section 6: the one table of which trigger moves an order
 * from which status to which.
 *
 * <p>It is the only entry point for changing an order's status. {@link Order} has no such method,
 * and the table lives here, in {@code libs/share}, so that {@code services/api} (checkout) and
 * {@code apps/admin} (quote terms) cannot each keep a copy that drifts.
 *
 * <p>The table is keyed on (status, trigger), not (from, to), so the several roads into {@link
 * OrderStatus#CANCELLED} stay distinguishable. The edges:
 *
 * <ul>
 *   <li>{@code CREATED} → {@code AWAITING_PAYMENT} on {@link TransitionTrigger#CHECKOUT_SUBMITTED}
 *   <li>{@code AWAITING_PAYMENT} → {@code PAID} on {@link TransitionTrigger#PAYMENT_CAPTURED}
 *   <li>{@code AWAITING_PAYMENT} → {@code AWAITING_PAYMENT} on {@link
 *       TransitionTrigger#PAYMENT_FAILED}
 *   <li>{@code AWAITING_PAYMENT} → {@code CANCELLED} on {@link TransitionTrigger#PAYMENT_ABANDONED}
 *       or {@link TransitionTrigger#CUSTOMER_CANCELLED}
 *   <li>{@code AWAITING_PAYMENT} → {@code AWAITING_MANAGER_QUOTE} on {@link
 *       TransitionTrigger#TIER_EXCEEDED}
 *   <li>{@code AWAITING_MANAGER_QUOTE} → {@code AWAITING_PAYMENT} on {@link
 *       TransitionTrigger#QUOTE_SET}
 *   <li>{@code AWAITING_MANAGER_QUOTE} → {@code CANCELLED} on {@link
 *       TransitionTrigger#QUOTE_DECLINED}
 *   <li>{@code PAID} → {@code FULFILLING} → {@code SHIPPED} → {@code DELIVERED} on {@link
 *       TransitionTrigger#FULFILLMENT_STARTED}, {@link TransitionTrigger#SHIPPED} and {@link
 *       TransitionTrigger#DELIVERED}
 * </ul>
 *
 * <p><strong>Reading of the diagram.</strong> Architecture section 6 draws one arrow into {@code
 * CANCELLED}, from {@code AWAITING_PAYMENT}, annotated with three causes. The quote-declined cause
 * can only happen in {@code AWAITING_MANAGER_QUOTE}, so that edge exists too; otherwise a declined
 * quote would strand the order. <strong>Payment failure and BRD FR-6.3:</strong> FR-6.3 says a
 * failed payment returns the customer to payment selection with everything intact, so a single
 * failed attempt must not cancel. {@code PAYMENT_FAILED} is therefore a legal event that leaves the
 * order in {@code AWAITING_PAYMENT} (it is in the table, so callers get the unchanged status back
 * rather than a conflict for an ordinary outcome), and the diagram's "payment failure" cancellation
 * is the separate {@code PAYMENT_ABANDONED}.
 *
 * <p>Terminal statuses ({@link OrderStatus#isTerminal()}) accept no trigger. Who may fire a trigger
 * is authorisation, and what a transition notifies or records is not decided here.
 */
public final class OrderStateMachine {

  private static final List<OrderTransition> EDGES =
      List.of(
          new OrderTransition(
              OrderStatus.CREATED,
              TransitionTrigger.CHECKOUT_SUBMITTED,
              OrderStatus.AWAITING_PAYMENT),
          new OrderTransition(
              OrderStatus.AWAITING_PAYMENT, TransitionTrigger.PAYMENT_CAPTURED, OrderStatus.PAID),
          new OrderTransition(
              OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.PAYMENT_FAILED,
              OrderStatus.AWAITING_PAYMENT),
          new OrderTransition(
              OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.PAYMENT_ABANDONED,
              OrderStatus.CANCELLED),
          new OrderTransition(
              OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.CUSTOMER_CANCELLED,
              OrderStatus.CANCELLED),
          new OrderTransition(
              OrderStatus.AWAITING_PAYMENT,
              TransitionTrigger.TIER_EXCEEDED,
              OrderStatus.AWAITING_MANAGER_QUOTE),
          new OrderTransition(
              OrderStatus.AWAITING_MANAGER_QUOTE,
              TransitionTrigger.QUOTE_SET,
              OrderStatus.AWAITING_PAYMENT),
          new OrderTransition(
              OrderStatus.AWAITING_MANAGER_QUOTE,
              TransitionTrigger.QUOTE_DECLINED,
              OrderStatus.CANCELLED),
          new OrderTransition(
              OrderStatus.PAID, TransitionTrigger.FULFILLMENT_STARTED, OrderStatus.FULFILLING),
          new OrderTransition(
              OrderStatus.FULFILLING, TransitionTrigger.SHIPPED, OrderStatus.SHIPPED),
          new OrderTransition(
              OrderStatus.SHIPPED, TransitionTrigger.DELIVERED, OrderStatus.DELIVERED));

  private OrderStateMachine() {}

  /**
   * Returns the status an order moves to.
   *
   * @param from the status the order is in
   * @param trigger why it is moving
   * @return the new status, which equals {@code from} for {@link TransitionTrigger#PAYMENT_FAILED}
   * @throws IllegalTransitionException if the status does not accept the trigger; it is never
   *     answered with an empty result, so an illegal move cannot be silently ignored
   * @throws ValidationException if an argument is null
   */
  public static OrderStatus transition(OrderStatus from, TransitionTrigger trigger) {
    return resolve(from, trigger).to();
  }

  /**
   * Returns the legal edge for a status and a trigger, which keeps the reason beside both statuses.
   *
   * @param from the status the order is in
   * @param trigger why it is moving
   * @return the edge
   * @throws IllegalTransitionException if the status does not accept the trigger
   * @throws ValidationException if an argument is null
   */
  public static OrderTransition resolve(OrderStatus from, TransitionTrigger trigger) {
    requireArguments(from, trigger);
    return edgeFor(from, trigger)
        .orElseThrow(() -> new IllegalTransitionException(from, trigger, availableFrom(from)));
  }

  /**
   * Tells whether a status accepts a trigger.
   *
   * @param from the status the order is in
   * @param trigger the trigger
   * @return {@code true} if {@link #transition} would succeed
   * @throws ValidationException if an argument is null
   */
  public static boolean canTransition(OrderStatus from, TransitionTrigger trigger) {
    requireArguments(from, trigger);
    return edgeFor(from, trigger).isPresent();
  }

  /**
   * Lists the triggers a status accepts, which is what drives the actions a screen may offer.
   *
   * @param from the status the order is in
   * @return the accepted triggers, unmodifiable and empty for a terminal status
   * @throws ValidationException if the status is null
   */
  public static Set<TransitionTrigger> availableFrom(OrderStatus from) {
    if (from == null) {
      throw new ValidationException("order-state.status-missing", "Order status must not be null");
    }
    final Set<TransitionTrigger> accepted = EnumSet.noneOf(TransitionTrigger.class);
    EDGES.stream()
        .filter(edge -> edge.from() == from)
        .forEach(edge -> accepted.add(edge.trigger()));
    return Collections.unmodifiableSet(accepted);
  }

  /**
   * Lists every legal edge.
   *
   * @return the edges, unmodifiable
   */
  public static List<OrderTransition> transitions() {
    return EDGES;
  }

  private static void requireArguments(OrderStatus from, TransitionTrigger trigger) {
    if (from == null || trigger == null) {
      throw new ValidationException(
          "order-state.argument-missing", "Order status and trigger must not be null");
    }
  }

  private static Optional<OrderTransition> edgeFor(OrderStatus from, TransitionTrigger trigger) {
    return EDGES.stream()
        .filter(edge -> edge.from() == from && edge.trigger() == trigger)
        .findFirst();
  }
}
