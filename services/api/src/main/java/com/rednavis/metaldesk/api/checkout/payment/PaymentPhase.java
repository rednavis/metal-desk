package com.rednavis.metaldesk.api.checkout.payment;

/** Where a checkout session stands in paying for its order. */
public enum PaymentPhase {

  /** A method is chosen; no order exists yet. */
  METHOD_SELECTED,

  /**
   * The order exists and awaits payment, and no provider result is outstanding. This is where a
   * declined or failed payment leaves the session, so the customer can try again.
   */
  ORDER_CREATED,

  /** The provider needs the customer to complete something (a redirect or an element) first. */
  PENDING_CONFIRMATION,

  /** An invoice was issued; the order awaits payment against it. */
  INVOICE_ISSUED,

  /** The payment was captured and the order is paid. */
  PAID
}
