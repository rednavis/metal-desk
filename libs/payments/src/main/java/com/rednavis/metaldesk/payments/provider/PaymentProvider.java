package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import reactor.core.publisher.Mono;

/**
 * The one interface between checkout and any payment vendor (Architecture section 4).
 *
 * <p>Checkout calls this and never a vendor's SDK; an adapter implements it. That is what makes
 * swapping a WireMock-backed adapter for a real provider a configuration change rather than a code
 * change (ADR-0002), and it only holds if nothing here is shaped like one vendor: no HTTP types, no
 * provider error strings, no redirect assumption particular to one gateway.
 *
 * <p><strong>It is reactive.</strong> A payment call is long-latency I/O, and the whole stack is
 * non-blocking (Architecture section 5), so both operations return a {@link Mono} and nothing in an
 * implementation may block.
 *
 * <p><strong>A decline is a value, a transport failure is an error.</strong> Each {@code Mono}
 * emits exactly one {@link PaymentOutcome}. A refusal by the provider is a {@link
 * PaymentOutcome.Declined} in that outcome, an ordinary result that returns the customer to payment
 * selection (BRD FR-6.3). Only a provider that cannot be reached or understood ends the {@code
 * Mono} with a {@link PaymentProviderException}. Any other error signal, such as a {@code
 * ValidationException} for a method the adapter does not serve, is a caller or programming error.
 *
 * <p>The surface is deliberately two operations. Refunds, later capture and voids are not here: no
 * requirement in scope needs them, and each is a second state machine.
 */
public interface PaymentProvider {

  /**
   * Describes which provider this is and which payment methods it serves.
   *
   * @return the capability, never null
   */
  ProviderCapability capability();

  /**
   * Tells whether this provider serves a payment method.
   *
   * @param method the method
   * @return {@code true} if {@link #capability()} lists it
   */
  default boolean supports(PaymentMethod method) {
    return capability().supports(method);
  }

  /**
   * Asks the provider to take a payment.
   *
   * @param intent what to take, for which order, by which method
   * @return a {@code Mono} of the one outcome; it errors with {@link PaymentProviderException} only
   *     if the provider cannot be reached or understood
   */
  Mono<PaymentOutcome> authorise(PaymentIntent intent);

  /**
   * Completes a payment that finished outside the request, after a {@link
   * PaymentOutcome.RedirectRequired} or {@link PaymentOutcome.ElementRequired}.
   *
   * @param reference the handle the earlier outcome carried
   * @return a {@code Mono} of the final outcome, which is {@link PaymentOutcome.Captured} or a
   *     decline or failure; it errors with {@link PaymentProviderException} only if the provider
   *     cannot be reached or understood
   */
  Mono<PaymentOutcome> confirm(ProviderReference reference);
}
