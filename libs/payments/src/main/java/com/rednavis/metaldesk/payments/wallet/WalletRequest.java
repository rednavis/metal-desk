package com.rednavis.metaldesk.payments.wallet;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;

/**
 * What is sent to the wallet provider to take a payment from the customer's wallet account: the
 * wire form of a {@link PaymentIntent}, and nothing more. It carries references and an amount,
 * never an instrument; the amount is a decimal string, never a floating-point number.
 *
 * @param orderId the order being paid
 * @param amount the amount as a plain decimal string
 * @param currency the ISO-4217 currency code
 * @param customerId the customer whose wallet account is charged
 */
record WalletRequest(String orderId, String amount, String currency, String customerId) {

  /**
   * Builds the request for an intent.
   *
   * @param intent what checkout asked for
   * @return the wire request
   */
  /* default */ static WalletRequest from(PaymentIntent intent) {
    return new WalletRequest(
        intent.orderId().value(),
        intent.amount().amount().toPlainString(),
        intent.amount().currency().code(),
        intent.customerId().value());
  }
}
