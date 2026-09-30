package com.rednavis.metaldesk.api.checkout.delivery;

/**
 * Which actions a checkout session currently permits, decided by the delivery evaluation (BRD
 * FR-5.2, FR-5.3) and enforced on the server.
 */
public enum CheckoutStage {

  /** Within every ceiling: delivery is priced automatically and the customer may pay. */
  PAYMENT_ALLOWED,

  /**
   * Over a ceiling, or no tier for the region: a human must price the order, so payment is refused
   * and the manager handoff is the only way forward.
   */
  HANDOFF_REQUIRED
}
