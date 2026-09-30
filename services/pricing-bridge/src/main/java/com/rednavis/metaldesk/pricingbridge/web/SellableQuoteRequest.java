package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;

/**
 * What to price: a product's physical description and the margin rule that applies to it.
 *
 * <p>The bridge holds no catalog, so the caller says what the product is and which rule applies;
 * the price comes from the latest reference price of the metal.
 *
 * @param scope what the margin rule applies to
 * @param metal the metal the product is made of
 * @param purity the fineness in parts per thousand as decimal text, for example {@code "999.9"}
 * @param weight the weight as decimal text
 * @param weightUnit the unit of the weight
 * @param marginPercent the margin as decimal text, from 0 to 100
 */
public record SellableQuoteRequest(
    Scope scope,
    Metal metal,
    String purity,
    String weight,
    WeightUnit weightUnit,
    String marginPercent) {

  /** What the margin rule applies to. */
  public enum ScopeType {
    /** One product. */
    PRODUCT,
    /** Every product of a category. */
    CATEGORY
  }

  /**
   * The scope of the rule.
   *
   * @param type whether {@code id} names a product or a category
   * @param id the product or category id
   */
  public record Scope(ScopeType type, String id) {}
}
