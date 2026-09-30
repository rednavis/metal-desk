package com.rednavis.metaldesk.admin.order.dto;

/** Which limit of the destination region's tiers an order exceeds (BRD FR-5.3). */
public enum BoundCeiling {
  /** The order's value before tax is above the region's widest value ceiling. */
  VALUE,
  /** The order's weight is above the region's widest weight ceiling. */
  WEIGHT,
  /** The region has no tier at all, so every order to it needs a manager. */
  NO_TIER_FOR_REGION,
  /** Some tier accepts the order as configured now; the handoff predates a change of tiers. */
  WITHIN_TIERS
}
