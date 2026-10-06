package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionStore;
import com.rednavis.metaldesk.api.checkout.delivery.PaymentGate;
import com.rednavis.metaldesk.api.checkout.payment.dto.PaymentMethodsView;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Step 2, payment method (BRD FR-6.1): which methods a checkout may use, and choosing one.
 *
 * <p>The offer is <strong>computed per session</strong>. It starts from every method some provider
 * supports ({@link ProviderRegistry}) and applies the high-value restriction (BR-9, {@link
 * PaymentMethodPolicy}) to the order's grand total: above the ceiling the gateway methods disappear
 * and invoice, which is never filtered out, remains.
 *
 * <p>Like every payment operation it starts with the {@link PaymentGate}, so a session that needs a
 * manager is refused (409) before anything else.
 */
@Service
@RequiredArgsConstructor
public class PaymentMethodOfferService {

  private final PaymentGate gate;
  private final ProviderRegistry registry;
  private final PaymentMethodPolicy policy;
  private final CheckoutSessionStore store;

  /**
   * Lists the methods on offer.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the methods, the total they were computed for, and any method chosen
   */
  public Mono<PaymentMethodsView> offer(String id, AuthenticatedCustomer customer) {
    return gate.require(id, customer).map(this::viewOf);
  }

  /**
   * Chooses a payment method.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @param method the method, which must be on offer
   * @return the offer, with the method now selected
   * @throws ValidationException {@code payment.method-not-offered}
   * @throws ConflictException {@code checkout.payment-in-progress} while a payment is in flight
   */
  public Mono<PaymentMethodsView> select(
      String id, AuthenticatedCustomer customer, PaymentMethod method) {
    return gate.require(id, customer)
        .flatMap(
            session -> {
              if (method == null || !offered(session).contains(method)) {
                return Mono.error(
                    new ValidationException(
                        "payment.method-not-offered",
                        "That payment method is not available for this order"));
              }
              if (inFlight(session)) {
                return Mono.error(
                    new ConflictException(
                        "checkout.payment-in-progress",
                        "A payment is in progress; the method can no longer be changed"));
              }
              return store.update(id, current -> current.withPayment(chosen(current, method)));
            })
        .map(this::viewOf);
  }

  private static PaymentState chosen(CheckoutSession session, PaymentMethod method) {
    return session
        .payment()
        .map(state -> state.withMethod(method))
        .orElseGet(() -> PaymentState.selected(method));
  }

  private static boolean inFlight(CheckoutSession session) {
    return session
        .payment()
        .map(
            state ->
                state.phase() == PaymentPhase.PENDING_CONFIRMATION
                    || state.phase() == PaymentPhase.INVOICE_ISSUED
                    || state.phase() == PaymentPhase.PAID)
        .orElse(false);
  }

  private List<PaymentMethod> offered(CheckoutSession session) {
    final Money total = PaymentAmounts.of(session).grandTotal();
    return Arrays.stream(PaymentMethod.values())
        .filter(method -> registry.supportedMethods().contains(method))
        .filter(method -> policy.allows(method, total))
        .toList();
  }

  private PaymentMethodsView viewOf(CheckoutSession session) {
    final OrderTotals totals = PaymentAmounts.of(session);
    return PaymentViews.methods(
        session, offered(session), totals, policy.isHighValue(totals.grandTotal()));
  }
}
