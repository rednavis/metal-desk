package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.account.LocaleParser;
import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutProperties;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.delivery.PaymentGate;
import com.rednavis.metaldesk.api.checkout.payment.dto.ExecutePaymentRequest;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentResultView;
import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Pays for a checkout (BRD FR-7.2): gets the order, calls the provider, applies the outcome.
 *
 * <p><strong>The order exists before the provider is called</strong> ({@link PaymentPreparation}),
 * so if the provider's answer never arrives an order awaiting payment can be reconciled; calling
 * the provider first would lose the customer's intent on every failure. The amount in the {@link
 * PaymentIntent} is the order's own grand total, the same figure the overview shows, and the
 * customer must have confirmed it.
 *
 * <p>Payment is refused, before anything else, on a session that needs a manager ({@link
 * PaymentGate}). The provider is found by what it supports ({@link ProviderRegistry}); nothing here
 * knows a vendor. What each outcome does is {@link PaymentOutcomeHandler}'s.
 */
@Service
@RequiredArgsConstructor
public class PaymentExecutionService {

  private final PaymentGate gate;
  private final PaymentPreparation preparation;
  private final ProviderRegistry registry;
  private final PaymentOutcomeHandler outcomes;
  private final CheckoutProperties properties;

  /**
   * Pays for the checkout with the selected method.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @param request the total the customer confirmed, and the locale
   * @return the result to show the customer
   * @throws com.rednavis.metaldesk.api.web.FieldViolationsException if the confirmed total is
   *     missing or malformed
   * @throws ConflictException {@code checkout.handoff-required}, {@code
   *     checkout.delivery-not-evaluated}, {@code checkout.method-not-selected}, {@code
   *     checkout.method-not-offered}, {@code checkout.total-changed}, {@code
   *     checkout.payment-in-progress} or {@code checkout.already-paid}
   */
  public Mono<PaymentResultView> execute(
      String id, AuthenticatedCustomer customer, ExecutePaymentRequest request) {
    final BigDecimal confirmed = ConfirmedTotal.of(request);
    final Locale locale = LocaleParser.parse(request == null ? null : request.locale());
    return gate.require(id, customer)
        .flatMap(PaymentExecutionService::requireReady)
        .flatMap(session -> preparation.prepare(session, customer, confirmed))
        .flatMap(prepared -> charge(prepared.session(), prepared.order(), locale));
  }

  private Mono<PaymentResultView> charge(CheckoutSession session, Order order, Locale locale) {
    final PaymentMethod method = session.payment().flatMap(PaymentState::method).orElseThrow();
    final PaymentProvider provider =
        registry
            .forMethod(method)
            .orElseThrow(
                () ->
                    new ConflictException(
                        "checkout.method-unavailable", "No provider serves that payment method"));
    final String providerId = provider.capability().providerId();
    final PaymentIntent intent =
        new PaymentIntent(
            order.id(),
            order.totals().grandTotal(),
            method,
            order.customerId(),
            returnUri(session, "return"),
            returnUri(session, "cancel"),
            locale);
    return provider
        .authorise(intent)
        .flatMap(outcome -> outcomes.handle(session.id(), order, providerId, method, outcome))
        .onErrorResume(
            PaymentProviderException.class,
            failure -> outcomes.unavailable(session.id(), order, providerId, failure));
  }

  private URI returnUri(CheckoutSession session, String leaf) {
    return URI.create(properties.clientBaseUrl() + "/" + session.id() + "/" + leaf);
  }

  private static Mono<CheckoutSession> requireReady(CheckoutSession session) {
    final PaymentState state = session.payment().orElse(null);
    Mono<CheckoutSession> ready = Mono.just(session);
    if (state == null || state.method().isEmpty()) {
      ready =
          Mono.error(
              new ConflictException(
                  "checkout.method-not-selected", "Choose a payment method first"));
    } else if (state.phase() == PaymentPhase.PAID) {
      ready = Mono.error(new ConflictException("checkout.already-paid", "This order is paid"));
    } else if (state.phase() == PaymentPhase.PENDING_CONFIRMATION
        || state.phase() == PaymentPhase.INVOICE_ISSUED) {
      ready = Mono.error(PaymentPreparation.inProgress());
    }
    return ready;
  }
}
