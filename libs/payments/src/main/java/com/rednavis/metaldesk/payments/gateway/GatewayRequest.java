package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import java.util.Locale;

/**
 * What is sent to the gateway to take a payment: the wire form of a {@link PaymentIntent}, and
 * nothing more. Like the intent it carries references and an amount, never an instrument.
 *
 * <p>The amount is a decimal string, never a floating-point number, so no precision is lost on the
 * wire.
 *
 * @param orderId the order being paid
 * @param amount the amount as a plain decimal string
 * @param currency the ISO-4217 currency code
 * @param method the payment method, lower-cased
 * @param customerId the paying customer
 * @param returnUrl where the gateway sends the customer after completing
 * @param cancelUrl where the gateway sends the customer if they back out
 */
record GatewayRequest(
    String orderId,
    String amount,
    String currency,
    String method,
    String customerId,
    String returnUrl,
    String cancelUrl) {

  /**
   * Builds the request for an intent.
   *
   * @param intent what checkout asked for
   * @return the wire request
   */
  /* default */ static GatewayRequest from(PaymentIntent intent) {
    return new GatewayRequest(
        intent.orderId().value(),
        intent.amount().amount().toPlainString(),
        intent.amount().currency().code(),
        intent.method().name().toLowerCase(Locale.ROOT),
        intent.customerId().value(),
        intent.returnUri().toString(),
        intent.cancelUri().toString());
  }
}
