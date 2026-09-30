package com.rednavis.metaldesk.api.persistence.document;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import java.time.Instant;

/**
 * A sellable price as stored: the unit price together with the rule and the reference price it was
 * derived from, so an order line can be explained after both have changed (BRD BR-2).
 *
 * @param unitPrice the price of one unit
 * @param marginPercent the margin of the rule that applied, as decimal text
 * @param scopeKind whether the rule applied to a product or to a category
 * @param scopeId the id of that product or category
 * @param reference the reference price the unit price was derived from
 */
public record PriceDocument(
    MoneyDocument unitPrice,
    String marginPercent,
    ScopeKind scopeKind,
    String scopeId,
    ReferenceDocument reference) {

  /**
   * The reference price a sellable price was derived from.
   *
   * @param metal the metal
   * @param pricePerGram the price per gram
   * @param observedAt when the price was observed
   */
  public record ReferenceDocument(Metal metal, MoneyDocument pricePerGram, Instant observedAt) {}

  /** What a price rule applied to. */
  public enum ScopeKind {
    /** The rule applied to one product. */
    PRODUCT,
    /** The rule applied to a whole category. */
    CATEGORY
  }
}
