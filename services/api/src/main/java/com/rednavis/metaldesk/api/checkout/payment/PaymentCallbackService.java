package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.delivery.PaymentGate;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The out-of-band return of a redirect or element payment (BRD FR-7.2), which nobody has
 * authenticated.
 *
 * <p><strong>A callback is a claim, never evidence.</strong> All it supplies is which checkout and
 * which provider reference it is about. This service checks that the reference is the one the
 * checkout is actually waiting on, asks the <em>provider</em> to {@code confirm} that reference,
 * and derives the result from the provider's answer alone. A {@code status=success} parameter, or
 * any other query parameter, is never read, so "a callback saying success" cannot mark an order
 * paid. Everything that is not a live, matching, still-awaited payment gets the same generic 400.
 *
 * <p>The payment gate is applied here too, so a session that needs a manager is refused.
 */
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

  private final CheckoutSessionStore store;
  private final PaymentGate gate;
  private final ProviderRegistry registry;
  private final PaymentOrders orders;
  private final PaymentOutcomeHandler outcomes;

  /**
   * Confirms a returned payment with its provider.
   *
   * @param checkoutId the checkout session's id, from the return address
   * @param reference the provider reference, from the return address
   * @return the result, derived from the provider's confirmation
   * @throws ValidationException {@code payment.callback-invalid} for anything that does not match
   */
  public Mono<PaymentResultView> confirm(String checkoutId, String reference) {
    return store
        .find(checkoutId == null ? "" : checkoutId)
        .switchIfEmpty(Mono.error(invalid()))
        .flatMap(gate::check)
        .flatMap(session -> verify(session, reference));
  }

  private Mono<PaymentResultView> verify(CheckoutSession session, String reference) {
    final PaymentState state = session.payment().orElse(null);
    Mono<PaymentResultView> result = Mono.error(invalid());
    if (state != null && state.phase() == PaymentPhase.PAID) {
      result =
          orders
              .find(state.order().orElseThrow())
              .map(order -> PaymentResults.of(order, "CAPTURED", null, null, null));
    } else if (state != null && awaited(state, reference)) {
      result = confirmWithProvider(session, state);
    }
    return result;
  }

  private static boolean awaited(PaymentState state, String reference) {
    return state.phase() == PaymentPhase.PENDING_CONFIRMATION
        && state
            .reference()
            .map(ProviderReference::value)
            .filter(value -> value.equals(reference))
            .isPresent();
  }

  private Mono<PaymentResultView> confirmWithProvider(CheckoutSession session, PaymentState state) {
    final PaymentMethod method = state.method().orElseThrow();
    final PaymentProvider provider =
        registry.forMethod(method).orElseThrow(PaymentCallbackService::invalid);
    final String providerId = provider.capability().providerId();
    return orders
        .find(state.order().orElseThrow())
        .flatMap(
            order ->
                provider
                    .confirm(state.reference().orElseThrow())
                    .flatMap(
                        outcome ->
                            outcomes.handle(session.id(), order, providerId, method, outcome))
                    .onErrorResume(
                        PaymentProviderException.class,
                        failure -> outcomes.confirmationUnavailable(order, providerId, failure)));
  }

  private static ValidationException invalid() {
    return new ValidationException(
        "payment.callback-invalid", "That payment return could not be matched to a payment");
  }
}
