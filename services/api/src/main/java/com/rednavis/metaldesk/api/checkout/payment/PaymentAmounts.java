package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryState;
import com.rednavis.metaldesk.share.domain.order.OrderTotals;
import com.rednavis.metaldesk.share.domain.order.OrderTotalsCalculator;

/**
 * The one way an order's totals are computed for checkout: BRD BR-5's {@code Σ(unit price ×
 * quantity) + tax + delivery cost}, from the session's lines and the delivery quote. The overview
 * the customer sees, the method restriction and the amount the provider is asked to charge all come
 * from here, so they cannot drift apart.
 */
public final class PaymentAmounts {

  private PaymentAmounts() {}

  /**
   * Computes the totals of an evaluated session.
   *
   * @param session a session whose delivery was evaluated with an automatic quote
   * @return net, tax, delivery and grand total
   * @throws java.util.NoSuchElementException if the session has no quote
   */
  public static OrderTotals of(CheckoutSession session) {
    return OrderTotalsCalculator.compute(
        session.lines(), session.delivery().flatMap(DeliveryState::quote).orElseThrow().cost());
  }
}
