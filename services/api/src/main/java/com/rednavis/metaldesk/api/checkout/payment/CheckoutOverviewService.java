package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.checkout.delivery.PaymentGate;
import com.rednavis.metaldesk.api.checkout.payment.dto.OverviewView;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The final order overview (BRD FR-7.1): the customer and delivery details, the selected payment
 * method, the line items, tax, delivery cost and grand total.
 *
 * <p>It is <strong>computed, never stored</strong>, from the session's lines and the delivery quote
 * by {@link PaymentAmounts}, the same calculation that decides the amount charged. So the grand
 * total shown here is, to the cent, the amount the provider is asked for (a test compares the two
 * real values). Every earlier step stays editable until payment is in flight.
 */
@Service
@RequiredArgsConstructor
public class CheckoutOverviewService {

  private final PaymentGate gate;
  private final PaymentOrders orders;

  /**
   * Builds the overview.
   *
   * @param id the checkout session's id
   * @param customer the signed-in customer, or null for a guest
   * @return the overview
   */
  public Mono<OverviewView> overview(String id, AuthenticatedCustomer customer) {
    return gate.require(id, customer)
        .flatMap(
            session ->
                session
                    .payment()
                    .flatMap(PaymentState::order)
                    .map(
                        order ->
                            orders.find(order).map(found -> Optional.of(found.number().format())))
                    .orElseGet(() -> Mono.just(Optional.<String>empty()))
                    .map(reference -> PaymentViews.overview(session, reference)));
  }
}
