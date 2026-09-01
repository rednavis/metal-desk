package com.rednavis.metaldesk.share.domain.catalog;

import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;

/**
 * The physical description of a product: the specification block of the product detail page (BRD
 * FR-1.3).
 *
 * <p>It is a record of its own, not fields on {@link Product}, so the catalog projection (T-031)
 * can expose a specification without exposing the aggregate.
 *
 * <p>Dimensions are optional display text such as {@code "50 x 30 x 2 mm"}: the legacy catalog kept
 * them as free text too, no calculation uses them, and some products (a bar "depending on the
 * manufacturer") have no fixed size.
 *
 * @param metal the metal the product is made of, never null
 * @param purity the fineness, never null
 * @param weight the weight, never null
 * @param dimensions the size as display text, or empty when there is none; never null, and when
 *     present it is trimmed and not blank
 */
public record ProductSpecification(
    Metal metal, Purity purity, Weight weight, Optional<String> dimensions) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the metal, purity, weight or dimensions optional is null, or the
   *     dimensions text is blank
   */
  public ProductSpecification {
    if (metal == null) {
      throw new ValidationException("specification.metal-missing", "Metal must not be null");
    }
    if (purity == null) {
      throw new ValidationException("specification.purity-missing", "Purity must not be null");
    }
    if (weight == null) {
      throw new ValidationException("specification.weight-missing", "Weight must not be null");
    }
    if (dimensions == null) {
      throw new ValidationException(
          "specification.dimensions-missing", "Dimensions must be an Optional, not null");
    }
    if (dimensions.filter(String::isBlank).isPresent()) {
      throw new ValidationException(
          "specification.dimensions-blank", "Dimensions must not be blank when present");
    }
    dimensions = dimensions.map(String::strip);
  }
}
