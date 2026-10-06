package com.rednavis.metaldesk.share.domain.payment;

/**
 * Where a payment stands. A decline is an <em>outcome</em> of a payment, recorded here, not an
 * exception: it is what the provider answered, and the customer returns to payment selection (BRD
 * FR-6.3).
 */
public enum PaymentStatus {

  /** Started but not yet settled. */
  PENDING,

  /** Funds were received. */
  CAPTURED,

  /** The provider refused the payment, for example for insufficient funds. */
  DECLINED,

  /** The payment could not be completed for a technical reason, such as a timeout. */
  FAILED,

  /** Funds were returned to the customer. */
  REFUNDED
}
