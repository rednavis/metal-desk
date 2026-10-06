package com.rednavis.metaldesk.api.checkout.confirmation.dto;

/** Which of the ways an order can be placed a confirmation is for (BRD FR-8.1). */
public enum ConfirmationKind {

  /** The payment was captured. */
  PAID,

  /** An invoice was issued; the order awaits the customer's payment of it. */
  INVOICE,

  /** The order was handed to a manager, who will send a price and terms. */
  MANAGER_QUOTE
}
