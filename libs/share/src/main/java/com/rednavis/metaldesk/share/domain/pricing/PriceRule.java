package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * A configured margin and what it applies to (BRD BR-3: "margin is configured per product or
 * product category, not hard-coded").
 *
 * <p>The rule carries no tax classification. Tax is a property of the product's category (BRD BR-4,
 * see {@code TaxCategory}); a second copy here would let the two disagree.
 *
 * <p>Which rule applies to a product is the caller's lookup, not this type's: nothing here can see
 * a product's category, and the BRD does not say what happens when both a product rule and a
 * category rule exist. The expected convention is that the more specific product rule wins, but
 * that is for the rule store (T-040) to pin down.
 *
 * @param margin the markup over the reference price, never null
 * @param scope what the rule applies to, never null
 */
public record PriceRule(Margin margin, Scope scope) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the margin or scope is null
   */
  public PriceRule {
    if (margin == null) {
      throw new ValidationException(
          "price-rule.margin-missing", "Price rule margin must not be null");
    }
    if (scope == null) {
      throw new ValidationException(
          "price-rule.scope-missing", "Price rule scope must not be null");
    }
  }

  /** What a {@link PriceRule} applies to. Sealed, so a {@code switch} over it is exhaustive. */
  public sealed interface Scope permits Scope.ForProduct, Scope.ForCategory {

    /**
     * The rule applies to one product.
     *
     * @param productId the product, never null
     */
    record ForProduct(ProductId productId) implements Scope {

      /**
       * Validates the product id.
       *
       * @throws ValidationException if the product id is null
       */
      public ForProduct {
        if (productId == null) {
          throw new ValidationException(
              "price-rule.product-missing", "Price rule product id must not be null");
        }
      }
    }

    /**
     * The rule applies to every product in one category.
     *
     * @param categoryId the category, never null
     */
    record ForCategory(CategoryId categoryId) implements Scope {

      /**
       * Validates the category id.
       *
       * @throws ValidationException if the category id is null
       */
      public ForCategory {
        if (categoryId == null) {
          throw new ValidationException(
              "price-rule.category-missing", "Price rule category id must not be null");
        }
      }
    }
  }
}
