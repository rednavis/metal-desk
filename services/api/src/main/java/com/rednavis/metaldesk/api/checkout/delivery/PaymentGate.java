package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.CheckoutSessionService;
import com.rednavis.metaldesk.share.error.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The server-side refusal to take payment on an order a human has to price (BRD FR-5.3: "does not
 * attempt to collect payment inline").
 *
 * <p>Every payment endpoint (T-037) calls {@link #require} <em>first</em>, before it talks to any
 * provider, and acts only on the session it returns. A session whose stage is anything other than
 * {@link CheckoutStage#PAYMENT_ALLOWED} is a 409, so a crafted request that offers "Pay" anyway
 * reaches no provider. The gate is deliberately here and not in the client, which is not a trust
 * boundary.
 *
 * <p>The stage is the outcome of the <em>last</em> evaluation, so a payment step must be preceded
 * by one: an unevaluated session is refused too, with its own code.
 */
@Component
@RequiredArgsConstructor
public class PaymentGate {

  /** The code of a payment attempt on a session that needs a manager. */
  public static final String HANDOFF_REQUIRED = "checkout.handoff-required";

  /** The code of a payment attempt before the delivery was evaluated. */
  public static final String NOT_EVALUATED = "checkout.delivery-not-evaluated";

  private final CheckoutSessionService sessions;

  /**
   * Lets a payment proceed only if the session permits it.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the session, when payment is allowed
   * @throws ConflictException {@code checkout.handoff-required} or {@code
   *     checkout.delivery-not-evaluated}
   */
  public Mono<CheckoutSession> require(String id, AuthenticatedCustomer customer) {
    return sessions
        .find(id, customer)
        .flatMap(
            session -> {
              final CheckoutStage stage = session.delivery().map(DeliveryState::stage).orElse(null);
              return stage == CheckoutStage.PAYMENT_ALLOWED
                  ? Mono.just(session)
                  : Mono.error(refusal(stage));
            });
  }

  private static ConflictException refusal(CheckoutStage stage) {
    return stage == null
        ? new ConflictException(NOT_EVALUATED, "Delivery has not been evaluated for this checkout")
        : new ConflictException(
            HANDOFF_REQUIRED,
            "This order needs a manager's price and terms; payment is not available");
  }
}
