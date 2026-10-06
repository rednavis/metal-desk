package com.rednavis.metaldesk.share.domain.catalog;

/**
 * The metals a catalog product can be made of (BRD FR-1.3).
 *
 * <p>The set is the one the legacy pricing bridge polled reference prices for. It is a closed enum
 * because pricing (T-013, T-039) must map every metal to a reference-price feed, so adding a metal
 * is a deliberate change across the domain, not a catalog data edit. Base metals are not modelled
 * until a catalog needs them.
 */
public enum Metal {

  /** Gold. */
  GOLD,

  /** Silver. */
  SILVER,

  /** Platinum. */
  PLATINUM,

  /** Palladium. */
  PALLADIUM,

  /** Rhodium. */
  RHODIUM,

  /** Ruthenium. */
  RUTHENIUM
}
