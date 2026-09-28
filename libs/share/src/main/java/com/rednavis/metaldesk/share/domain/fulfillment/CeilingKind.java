package com.rednavis.metaldesk.share.domain.fulfillment;

/**
 * Which ceiling of a {@link FulfillmentTier} an order went over (BRD FR-5.3: "value or weight,
 * whichever binds first"). The handoff message to staff needs to say which.
 */
public enum CeilingKind {

  /** The order's ex-tax value is above the tier's value ceiling. */
  VALUE,

  /** The order's weight is above the tier's weight ceiling. */
  WEIGHT
}
