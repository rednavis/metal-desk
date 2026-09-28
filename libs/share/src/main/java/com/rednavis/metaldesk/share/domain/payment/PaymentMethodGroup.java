package com.rednavis.metaldesk.share.domain.payment;

/**
 * How a payment is processed (Architecture section 4): the three kinds of {@code PaymentProvider}
 * the checkout routes to, rather than a vendor.
 */
public enum PaymentMethodGroup {

  /**
   * Processed by a payment gateway: card, bank debit, bank redirect, bank transfer and saved
   * wallet. These are the methods BRD BR-9 disables above the configured order-value ceiling.
   */
  GATEWAY,

  /** The separate, account-based wallet-payment provider. */
  WALLET,

  /** An invoice: a document is generated and no live gateway call is made. */
  INVOICE
}
