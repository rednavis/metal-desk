package com.rednavis.metaldesk.share.domain.catalog;

import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;

/**
 * A group of catalog products (BRD FR-1.2) and the owner of their tax classification (BRD BR-4).
 *
 * <p>The optional parent is a bare {@link CategoryId}, not a tree type: no requirement in scope
 * walks a hierarchy, and a tree invites cycle bugs. Only the direct self-reference is refused here;
 * detecting a longer cycle needs the other categories, so it belongs to whatever persists them.
 *
 * @param id the category's identifier, never null
 * @param name the display name, trimmed and never blank
 * @param parent the parent category, or empty for a top-level category; never the category's own id
 * @param taxCategory the tax classification every product in this category takes, never null
 */
public record Category(
    CategoryId id, String name, Optional<CategoryId> parent, TaxCategory taxCategory) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the id, parent optional or tax category is null, the name is
   *     null or blank, or the parent is the category itself
   */
  public Category {
    if (id == null) {
      throw new ValidationException("category.id-missing", "Category id must not be null");
    }
    if (name == null || name.isBlank()) {
      throw new ValidationException(
          "category.name-blank", "Category name must not be null or blank");
    }
    name = name.strip();
    if (parent == null) {
      throw new ValidationException(
          "category.parent-missing", "Category parent must be an Optional, not null");
    }
    if (parent.filter(id::equals).isPresent()) {
      throw new ValidationException(
          "category.parent-self", "Category " + id.value() + " cannot be its own parent");
    }
    if (taxCategory == null) {
      throw new ValidationException(
          "category.tax-category-missing", "Category tax category must not be null");
    }
  }
}
