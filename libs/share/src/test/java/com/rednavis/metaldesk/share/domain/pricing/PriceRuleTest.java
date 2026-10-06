package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.pricing.PriceRule.Scope;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;

class PriceRuleTest {

  private static final Margin MARGIN = Margin.of("5");
  private static final String PRODUCT = "p-1";

  @Test
  void ruleCanBeScopedToProduct() {
    final Scope scope = new Scope.ForProduct(new ProductId(PRODUCT));
    assertEquals(scope, new PriceRule(MARGIN, scope).scope());
  }

  @Test
  void ruleCanBeScopedToCategory() {
    final Scope scope = new Scope.ForCategory(new CategoryId("c-1"));
    assertEquals(scope, new PriceRule(MARGIN, scope).scope());
  }

  @Test
  void scopeSwitchIsExhaustive() {
    final Scope scope = new Scope.ForProduct(new ProductId(PRODUCT));
    final String id =
        switch (scope) {
          case Scope.ForProduct(ProductId productId) -> productId.value();
          case Scope.ForCategory(CategoryId categoryId) -> categoryId.value();
        };
    assertEquals(PRODUCT, id);
  }

  @Test
  void missingMarginOrScopeIsRefused() {
    final Scope scope = new Scope.ForProduct(new ProductId(PRODUCT));
    assertEquals(
        "price-rule.margin-missing",
        assertThrows(ValidationException.class, () -> new PriceRule(null, scope)).code());
    assertEquals(
        "price-rule.scope-missing",
        assertThrows(ValidationException.class, () -> new PriceRule(MARGIN, null)).code());
  }

  @Test
  void missingScopeTargetIsRefused() {
    assertEquals(
        "price-rule.product-missing",
        assertThrows(ValidationException.class, () -> new Scope.ForProduct(null)).code());
    assertEquals(
        "price-rule.category-missing",
        assertThrows(ValidationException.class, () -> new Scope.ForCategory(null)).code());
  }
}
