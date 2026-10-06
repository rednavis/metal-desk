package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.net.URI;
import java.util.Locale;

/** Synthetic payment intents for the fixture order. */
public final class InvoiceIntents {

  private InvoiceIntents() {}

  /**
   * Builds an invoice payment intent for the fixture order.
   *
   * @param locale the customer's locale
   * @return the intent
   */
  public static PaymentIntent invoice(Locale locale) {
    return of(locale, PaymentMethod.INVOICE);
  }

  /**
   * Builds a payment intent for the fixture order.
   *
   * @param locale the customer's locale
   * @param method the payment method
   * @return the intent
   */
  public static PaymentIntent of(Locale locale, PaymentMethod method) {
    return new PaymentIntent(
        InvoiceFixtures.ORDER_ID,
        InvoiceFixtures.eur("4261.94"),
        method,
        new CustomerId("c-1"),
        URI.create("https://shop.example/return"),
        URI.create("https://shop.example/cancel"),
        locale);
  }
}
