package com.rednavis.metaldesk.share.domain.customer;

/**
 * What a saved {@link Address} is used for. A customer may hold several addresses of each kind, so
 * the kind is a property of the address rather than the name of a field on the customer.
 */
public enum AddressKind {

  /** Where an order is delivered (BRD FR-4.1; pre-filled from the saved profile by FR-3.5). */
  DELIVERY,

  /** Where the invoice is addressed. */
  BILLING
}
