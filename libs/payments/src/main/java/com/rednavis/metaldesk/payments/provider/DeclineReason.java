package com.rednavis.metaldesk.payments.provider;

/**
 * Why a payment was declined, in terms that mean the same whichever vendor said no.
 *
 * <p>An adapter maps its vendor's codes into this enum; the enum itself holds no vendor code, no
 * vendor name and no HTTP status. A decline is an ordinary outcome that returns the customer to
 * payment selection with nothing lost (BRD FR-6.3), so it is reported as a {@link
 * PaymentOutcome.Declined}, never thrown.
 */
public enum DeclineReason {

  /** The customer's funds or limit do not cover the amount. */
  INSUFFICIENT_FUNDS,

  /** The customer's bank or the method's issuer refused the payment instrument. */
  INSTRUMENT_REJECTED,

  /** The customer did not complete, or failed, strong authentication. */
  AUTHENTICATION_FAILED,

  /** The provider's risk checks blocked the payment. */
  RISK_BLOCKED,

  /** The payment instrument or the payment session has expired. */
  EXPIRED,

  /** Any other refusal; the {@link PaymentOutcome.Declined} then carries a message safe to show. */
  OTHER
}
