package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.cart.CartStore;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.OrderTransitions;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.PaymentStatus;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Turns what a payment provider answered into what happens to the order and the checkout session,
 * for <strong>every</strong> {@link PaymentOutcome} (BRD FR-7.2). The switch over the sealed type
 * has no default branch, so a variant added later is a compile error here, not a silently swallowed
 * case.
 *
 * <ul>
 *   <li>{@code Captured}: record the payment, fire {@code PAYMENT_CAPTURED} so the order is {@code
 *       PAID}, hand off to confirmation.
 *   <li>{@code RedirectRequired}, {@code ElementRequired}: record a pending payment and wait for
 *       the customer to finish; the order stays {@code AWAITING_PAYMENT}.
 *   <li>{@code DocumentIssued}: record a pending invoice; the order stays {@code AWAITING_PAYMENT}.
 *   <li>{@code Declined}: <strong>not an error</strong>. The order stays {@code AWAITING_PAYMENT}
 *       (it is never cancelled), the session keeps everything, and the customer is returned to
 *       method selection with the reason (BRD FR-6.3).
 *   <li>{@code Failed}, or the provider being unreachable or incomprehensible: the same retention,
 *       reported as an error with an actionable message, and logged as an incident.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentOutcomeHandler {

  private static final String DECLINED_MESSAGE =
      "Your payment was declined. Choose a payment method and try again.";

  private final PaymentOrders orders;
  private final CheckoutSessionStore store;
  private final OrderSettlement settlement;
  private final CartStore carts;
  private final Clock clock;

  /**
   * Applies a provider's outcome.
   *
   * @param checkoutId the checkout session's id
   * @param order the order being paid for
   * @param providerId the provider that answered
   * @param method the method that was used
   * @param outcome what the provider answered
   * @return the result to show the customer
   */
  public Mono<PaymentResultView> handle(
      String checkoutId,
      Order order,
      String providerId,
      PaymentMethod method,
      PaymentOutcome outcome) {
    return switch (outcome) {
      case PaymentOutcome.Captured captured ->
          captured(checkoutId, order, providerId, method, captured.reference());
      case PaymentOutcome.RedirectRequired redirect ->
          pending(checkoutId, order, providerId, method, redirect.reference())
              .thenReturn(
                  PaymentResults.of(
                      order, "REDIRECT", redirect.redirectUri().toString(), null, null));
      case PaymentOutcome.ElementRequired element ->
          pending(checkoutId, order, providerId, method, element.reference())
              .thenReturn(PaymentResults.of(order, "ELEMENT", null, element.clientHandle(), null));
      case PaymentOutcome.DocumentIssued issued ->
          invoiced(checkoutId, order, providerId, method, issued.reference());
      case PaymentOutcome.Declined declined ->
          retryable(checkoutId, order)
              .thenReturn(
                  PaymentResults.declined(
                      order,
                      declined.reason().name(),
                      declined.message().orElse(DECLINED_MESSAGE)));
      case PaymentOutcome.Failed failed -> failure(checkoutId, order, providerId, failed.message());
    };
  }

  /**
   * Handles the provider not answering, or answering nonsense, to an authorisation.
   *
   * <p>This is <strong>not reported as a decline</strong>, and it is logged as an incident. The
   * session and order are retained exactly as for a decline. The message warns that the outcome is
   * unknown, since a timeout does not prove the payment failed.
   *
   * @param checkoutId the checkout session's id
   * @param order the order being paid for
   * @param providerId the provider
   * @param failure what went wrong
   * @return the result to show the customer
   */
  public Mono<PaymentResultView> unavailable(
      String checkoutId, Order order, String providerId, PaymentProviderException failure) {
    if (log.isErrorEnabled()) {
      log.error(
          "Payment provider {} failed for order {}: {}",
          providerId,
          order.number().format(),
          failure.kind(),
          failure);
    }
    return retryable(checkoutId, order)
        .thenReturn(
            PaymentResults.error(
                order, PaymentResults.code(failure), PaymentResults.message(order, failure)));
  }

  /**
   * Handles the provider not answering a <em>confirmation</em>. Nothing changes: the payment is
   * still awaiting the customer and the callback can be repeated.
   *
   * @param order the order being paid for
   * @param providerId the provider
   * @param failure what went wrong
   * @return the result to show
   */
  public Mono<PaymentResultView> confirmationUnavailable(
      Order order, String providerId, PaymentProviderException failure) {
    if (log.isErrorEnabled()) {
      log.error(
          "Payment provider {} could not confirm order {}: {}",
          providerId,
          order.number().format(),
          failure.kind(),
          failure);
    }
    return Mono.just(
        PaymentResults.error(
            order, PaymentResults.code(failure), PaymentResults.message(order, failure)));
  }

  private Mono<PaymentResultView> captured(
      String checkoutId,
      Order order,
      String providerId,
      PaymentMethod method,
      ProviderReference reference) {
    return order.status() == OrderStatus.PAID
        ? Mono.just(PaymentResults.of(order, "CAPTURED", null, null, null))
        : orders
            .save(
                OrderTransitions.advance(
                    OrderTransitions.withPayment(
                        order,
                        PaymentResults.record(
                            order, providerId, method, PaymentStatus.CAPTURED, reference),
                        clock.instant()),
                    TransitionTrigger.PAYMENT_CAPTURED,
                    clock.instant()))
            .flatMap(
                paid ->
                    phase(checkoutId, PaymentPhase.PAID, Optional.empty())
                        .flatMap(
                            session ->
                                removeCart(session).then(settlement.paid(paid, localeOf(session))))
                        .thenReturn(PaymentResults.of(paid, "CAPTURED", null, null, null)));
  }

  private Mono<Void> pending(
      String checkoutId,
      Order order,
      String providerId,
      PaymentMethod method,
      ProviderReference reference) {
    return orders
        .save(
            OrderTransitions.withPayment(
                order,
                PaymentResults.record(order, providerId, method, PaymentStatus.PENDING, reference),
                clock.instant()))
        .then(phase(checkoutId, PaymentPhase.PENDING_CONFIRMATION, Optional.of(reference)))
        .flatMap(this::removeCart);
  }

  private Mono<PaymentResultView> invoiced(
      String checkoutId,
      Order order,
      String providerId,
      PaymentMethod method,
      ProviderReference reference) {
    return orders
        .save(
            OrderTransitions.withPayment(
                order,
                PaymentResults.record(order, providerId, method, PaymentStatus.PENDING, reference),
                clock.instant()))
        .flatMap(
            saved ->
                phase(checkoutId, PaymentPhase.INVOICE_ISSUED, Optional.of(reference))
                    .flatMap(
                        session ->
                            removeCart(session)
                                .then(settlement.invoiceIssued(saved, localeOf(session))))
                    .thenReturn(
                        PaymentResults.of(
                            saved, "DOCUMENT_ISSUED", null, null, reference.value())));
  }

  private Mono<PaymentResultView> failure(
      String checkoutId, Order order, String providerId, String message) {
    if (log.isErrorEnabled()) {
      log.error("Payment failed for order {} via {}", order.number().format(), providerId);
    }
    return retryable(checkoutId, order)
        .thenReturn(PaymentResults.error(order, "payment.failed", message));
  }

  /** Records a failed attempt and returns the session to a state the customer can retry from. */
  private Mono<Void> retryable(String checkoutId, Order order) {
    return orders
        .failed(order)
        .then(phase(checkoutId, PaymentPhase.ORDER_CREATED, Optional.empty()))
        .then();
  }

  /**
   * Removes the cart the order was made from, once the payment has gone through or is under way:
   * the order owns those lines now, and the customer's next add starts a fresh cart. A declined or
   * failed payment keeps the cart. A cart that cannot be removed is logged and left, since the
   * payment already happened and must not fail for it.
   */
  private Mono<Void> removeCart(CheckoutSession session) {
    return session
        .cart()
        .map(cart -> carts.delete(cart.value()))
        .orElseGet(Mono::empty)
        .doOnError(failure -> log.warn("Could not remove the cart after a payment", failure))
        .onErrorResume(failure -> Mono.empty());
  }

  private Mono<CheckoutSession> phase(
      String checkoutId, PaymentPhase next, Optional<ProviderReference> reference) {
    return store.update(
        checkoutId,
        session -> session.withPayment(session.payment().orElseThrow().in(next, reference)));
  }

  private static Locale localeOf(CheckoutSession session) {
    return session.payment().map(PaymentState::locale).orElse(Locale.ENGLISH);
  }
}
