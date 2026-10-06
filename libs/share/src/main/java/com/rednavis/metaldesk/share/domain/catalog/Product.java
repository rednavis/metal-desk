package com.rednavis.metaldesk.share.domain.catalog;

import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;

/**
 * A catalog product (Architecture section 3; BRD FR-1.2, FR-1.3).
 *
 * <p><strong>The price is optional.</strong> A product without one is sold through the price
 * inquiry of BRD FR-9.1, so {@code price} is an {@link Optional} rather than a nullable {@link
 * Money} or a zero sentinel, and {@link #pricingMode()} is computed from it so the two cannot
 * disagree.
 *
 * <p><strong>A product's tax treatment is its category's</strong> (BRD BR-4): it is read through
 * {@link #category()} and deliberately not copied onto the product, so a rate or classification
 * change cannot leave products disagreeing with their category. Settled orders are protected
 * separately: an {@code OrderLine} snapshots the resolved treatment when the order is placed
 * (T-014, BRD BR-2), so reading it live from here never rewrites history.
 *
 * <p>Sellable-price derivation from a reference price and margin is BR-3 and lives with the price
 * rule (T-013); the {@code price} here is whatever fixed price the catalog holds.
 *
 * @param id the product's identifier, never null
 * @param name the display name, trimmed and never blank
 * @param specification the physical description, never null
 * @param category the category the product is listed under, never null
 * @param stock the availability, never null
 * @param price the fixed price, or empty when the product is priced on request; never null
 */
public record Product(
    ProductId id,
    String name,
    ProductSpecification specification,
    Category category,
    StockStatus stock,
    Optional<Money> price) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the id, specification, category, stock or price optional is
   *     null, or the name is null or blank
   */
  public Product {
    if (id == null) {
      throw new ValidationException("product.id-missing", "Product id must not be null");
    }
    if (name == null || name.isBlank()) {
      throw new ValidationException("product.name-blank", "Product name must not be null or blank");
    }
    name = name.strip();
    if (specification == null) {
      throw new ValidationException(
          "product.specification-missing", "Product specification must not be null");
    }
    if (category == null) {
      throw new ValidationException(
          "product.category-missing", "Product category must not be null");
    }
    if (stock == null) {
      throw new ValidationException("product.stock-missing", "Product stock must not be null");
    }
    if (price == null) {
      throw new ValidationException(
          "product.price-missing", "Product price must be an Optional, not null");
    }
  }

  /**
   * Returns how the product is priced, derived from whether it has a price.
   *
   * @return {@link PricingMode#FIXED} when {@link #price()} is present, otherwise {@link
   *     PricingMode#ON_REQUEST}
   */
  public PricingMode pricingMode() {
    return price.isPresent() ? PricingMode.FIXED : PricingMode.ON_REQUEST;
  }
}
