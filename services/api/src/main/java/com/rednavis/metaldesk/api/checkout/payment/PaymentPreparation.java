package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryEvaluationService;
import com.rednavis.metaldesk.api.checkout.delivery.PaymentGate;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gets the order a payment will be made for: the existing one on a retry, or a new one.
 *
 * <p><strong>The order is created here, before any provider is called.</strong> Creating it
 * snapshots the priced lines (BRD BR-2's price finality), takes the order number (BR-6) and moves
 * it to {@code AWAITING_PAYMENT}. A retry after a decline reuses the <em>same</em> order, so one
 * checkout is never two orders. Of two racing requests only the one that claims the session creates
 * the order; the other is refused ({@code checkout.payment-in-progress}).
 *
 * <p>Before an order is created the delivery is re-evaluated live, the total the customer confirmed
 * is compared with what would be charged, and the selected method is checked against the total
 * (BR-9), since a spot price may have moved since the overview was shown.
 */
@Component
@RequiredArgsConstructor
public class PaymentPreparation {

  private final DeliveryEvaluationService evaluation;
  private final PaymentGate gate;
  private final CheckoutSessionStore store;
  private final PaymentOrders orders;
  private final PaymentMethodPolicy policy;

  /**
   * The session and the order to pay for.
   *
   * @param session the session, with the order recorded on it
   * @param order the order, {@code AWAITING_PAYMENT}
   */
  public record Prepared(CheckoutSession session, Order order) {}

  /**
   * Prepares the order of a session.
   *
   * @param session a session that may be paid for, with a method selected
   * @param customer the signed-in customer, or null
   * @param confirmed the total the customer confirmed
   * @return the session and order
   */
  public Mono<Prepared> prepare(
      CheckoutSession session, AuthenticatedCustomer customer, BigDecimal confirmed) {
    return session
        .payment()
        .flatMap(PaymentState::order)
        .map(existing -> reuse(session, existing, confirmed))
        .orElseGet(() -> createFresh(session, customer, confirmed));
  }

  private Mono<Prepared> reuse(CheckoutSession session, OrderId existing, BigDecimal confirmed) {
    return orders
        .find(existing)
        .map(
            order -> {
              requireAwaitingPayment(order);
              final Money total = order.totals().grandTotal();
              requireConfirmed(total, confirmed);
              requireMethodOffered(session, total);
              return new Prepared(session, order);
            });
  }

  private Mono<Prepared> createFresh(
      CheckoutSession session, AuthenticatedCustomer customer, BigDecimal confirmed) {
    return evaluation
        .evaluate(session.id(), customer)
        .flatMap(gate::check)
        .flatMap(
            fresh -> {
              final Money total = PaymentAmounts.of(fresh).grandTotal();
              requireConfirmed(total, confirmed);
              requireMethodOffered(fresh, total);
              return claim(fresh);
            });
  }

  private Mono<Prepared> claim(CheckoutSession fresh) {
    return orders
        .create(fresh)
        .flatMap(
            order ->
                store
                    .update(fresh.id(), current -> withOrderOnce(current, order))
                    .flatMap(claimed -> save(claimed, order)));
  }

  private static CheckoutSession withOrderOnce(CheckoutSession current, Order order) {
    return current.payment().flatMap(PaymentState::order).isPresent()
        ? current
        : current.withPayment(current.payment().orElseThrow().withOrder(order.id()));
  }

  private Mono<Prepared> save(CheckoutSession claimed, Order order) {
    final boolean ours =
        claimed.payment().flatMap(PaymentState::order).filter(order.id()::equals).isPresent();
    return ours
        ? orders.save(order).map(saved -> new Prepared(claimed, saved))
        : Mono.error(inProgress());
  }

  private static void requireAwaitingPayment(Order order) {
    if (order.status() != OrderStatus.AWAITING_PAYMENT) {
      throw inProgress();
    }
  }

  private void requireMethodOffered(CheckoutSession session, Money total) {
    final PaymentMethod method = session.payment().flatMap(PaymentState::method).orElseThrow();
    if (!policy.allows(method, total)) {
      throw new ConflictException(
          "checkout.method-not-offered",
          "That payment method is not available for an order of this value");
    }
  }

  private static void requireConfirmed(Money total, BigDecimal confirmed) {
    if (total.amount().compareTo(confirmed) != 0) {
      throw new ConflictException(
          "checkout.total-changed",
          "The total is now "
              + total.amount().toPlainString()
              + " "
              + total.currency().code()
              + "; review the overview and confirm again");
    }
  }

  /**
   * The refusal of a payment while another is in flight.
   *
   * @return the conflict
   */
  public static ConflictException inProgress() {
    return new ConflictException(
        "checkout.payment-in-progress", "A payment for this checkout is already in progress");
  }
}
