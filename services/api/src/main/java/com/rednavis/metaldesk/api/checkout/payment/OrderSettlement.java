package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.order.Order;
import reactor.core.publisher.Mono;

/**
 * What happens after payment leaves the checkout: the hand-off to confirmation and notification
 * (T-038). Checkout calls it when an order is paid and when an invoice has been issued for one; the
 * default does nothing, and T-038 provides the bean that mails the confirmation and the invoice.
 */
public interface OrderSettlement {

  /**
   * An order was paid.
   *
   * @param order the order, now {@code PAID}
   * @return a signal that completes when the hand-off is done
   */
  Mono<Void> paid(Order order);

  /**
   * An invoice was issued for an order, which stays {@code AWAITING_PAYMENT}.
   *
   * @param order the order, carrying a pending invoice payment record
   * @return a signal that completes when the hand-off is done
   */
  Mono<Void> invoiceIssued(Order order);
}
