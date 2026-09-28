package com.rednavis.metaldesk.share.domain.catalog;

/**
 * How a product is priced (BRD FR-1.2). Never stored: {@link Product#pricingMode()} derives it from
 * whether the product has a price, so the mode and the price cannot disagree.
 */
public enum PricingMode {

  /** The product has a fixed price and can be added to a cart or bought directly (FR-3.1, 3.2). */
  FIXED,

  /**
   * The product has no price. This is the price-inquiry path of BRD FR-9.1, an ordinary way to sell
   * a product, not an error state.
   */
  ON_REQUEST
}
