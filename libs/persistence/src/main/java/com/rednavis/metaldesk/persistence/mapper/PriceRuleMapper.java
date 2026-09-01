package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.PriceDocument.ScopeKind;
import com.rednavis.metaldesk.persistence.document.PriceRuleDocument;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.pricing.Margin;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Maps a {@link PriceRule} to its {@link PriceRuleDocument} and back. */
@Component
public class PriceRuleMapper {

  /**
   * Rebuilds the domain rule.
   *
   * @param document the stored rule
   * @return the rule
   */
  public PriceRule toDomain(PriceRuleDocument document) {
    final PriceRule.Scope scope =
        switch (document.scopeKind()) {
          case PRODUCT -> new PriceRule.Scope.ForProduct(new ProductId(document.scopeId()));
          case CATEGORY -> new PriceRule.Scope.ForCategory(new CategoryId(document.scopeId()));
        };
    return new PriceRule(new Margin(new BigDecimal(document.marginPercent())), scope);
  }

  /**
   * Builds the document to store.
   *
   * @param rule the rule
   * @return the document
   */
  public PriceRuleDocument toDocument(PriceRule rule) {
    final ScopeKind kind;
    final String scopeId;
    switch (rule.scope()) {
      case PriceRule.Scope.ForProduct product -> {
        kind = ScopeKind.PRODUCT;
        scopeId = product.productId().value();
      }
      case PriceRule.Scope.ForCategory category -> {
        kind = ScopeKind.CATEGORY;
        scopeId = category.categoryId().value();
      }
    }
    return new PriceRuleDocument(
        PriceRuleDocument.idFor(kind, scopeId),
        kind,
        scopeId,
        rule.margin().percent().toPlainString());
  }
}
