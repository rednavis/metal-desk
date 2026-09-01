package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.Locale;
import reactor.core.publisher.Mono;

/**
 * What happens after payment leaves the checkout: the hand-off to confirmation and notification
 * (T-038). Checkout calls it when an order is paid and when an invoice has been issued for one; the
 * implementation in {@code checkout.confirmation} mails the confirmation and the invoice.
 */
public interface OrderSettlement {

  /**
   * An order was paid.
   *
   * @param order the order, now {@code PAID}
   * @param locale the customer's language
   * @return a signal that completes when the hand-off is done
   */
  Mono<Void> paid(Order order, Locale locale);

  /**
   * An invoice was issued for an order, which stays {@code AWAITING_PAYMENT}.
   *
   * @param order the order, carrying a pending invoice payment record
   * @param locale the customer's language
   * @return a signal that completes when the hand-off is done
   */
  Mono<Void> invoiceIssued(Order order, Locale locale);
}
