package com.rednavis.metaldesk.api.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A configured margin as stored (BRD BR-3).
 *
 * <p>The id is the scope's kind and id joined, for example {@code CATEGORY:cat-bars}, so a scope
 * can have at most one rule and a lookup by scope is a lookup by {@code _id}.
 *
 * @param id the scope kind and scope id, joined by a colon
 * @param scopeKind whether the rule applies to a product or a category
 * @param scopeId the id of that product or category
 * @param marginPercent the margin in percent, as decimal text
 */
@Document("price_rules")
public record PriceRuleDocument(
    @Id String id, PriceDocument.ScopeKind scopeKind, String scopeId, String marginPercent) {

  /**
   * Builds the id a rule for a scope is stored under.
   *
   * @param kind whether the scope is a product or a category
   * @param scopeId the product or category id
   * @return the document id
   */
  public static String idFor(PriceDocument.ScopeKind kind, String scopeId) {
    return kind + ":" + scopeId;
  }
}
