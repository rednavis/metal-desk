package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.share.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * What going back to edit an earlier step does to payment (BRD FR-7.1: any step can be edited).
 *
 * <p>A changed address or basket changes the delivery tier, so the delivery evaluation is dropped
 * and must be run again before anything can be paid; {@code CheckoutSession.withStep1} does that.
 * An order already created for the old details would be wrong, so it is <strong>cancelled</strong>
 * through the state machine and the session forgets it; the next payment creates a fresh one.
 *
 * <p>The exception is a payment in flight: while the customer is off completing a redirect, or an
 * invoice has been issued, or the order is paid, the earlier steps are locked (409 {@code
 * checkout.payment-in-progress}), because cancelling under a provider that may still capture would
 * lose track of money.
 */
@Component
@RequiredArgsConstructor
public class PaymentEditPolicy {

  private final PaymentOrders orders;
  private final CheckoutSessionStore store;

  /**
   * Prepares a session for an edit of step 1.
   *
   * @param session the session as it is now
   * @return a signal that completes when it is safe to edit
   * @throws ConflictException {@code checkout.payment-in-progress}
   */
  public Mono<Void> beforeEdit(CheckoutSession session) {
    final PaymentState state = session.payment().orElse(null);
    Mono<Void> ready = Mono.empty();
    if (state != null && state.order().isPresent()) {
      ready =
          state.phase() == PaymentPhase.ORDER_CREATED
              ? release(session, state)
              : Mono.error(
                  new ConflictException(
                      "checkout.payment-in-progress",
                      "A payment is in progress or done; earlier steps can no longer be edited"));
    }
    return ready;
  }

  private Mono<Void> release(CheckoutSession session, PaymentState state) {
    return orders
        .find(state.order().orElseThrow())
        .flatMap(orders::cancel)
        .then(
            store.update(
                session.id(),
                current -> current.withPayment(current.payment().orElse(state).withoutOrder())))
        .then();
  }
}
