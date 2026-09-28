package com.rednavis.metaldesk.share.domain.id;

/**
 * Identifies a catalog product. Distinct from every other identifier type, so the compiler rejects
 * swapped arguments.
 *
 * @param value the identifier value, never null or blank
 */
public record ProductId(String value) implements EntityId {

  /**
   * Validates the value.
   *
   * @throws com.rednavis.metaldesk.share.error.ValidationException if the value is null or blank
   */
  public ProductId {
    EntityId.requireValue(value, "ProductId");
  }
}
