package com.rednavis.metaldesk.share.domain.id;

/**
 * Identifies a customer. Distinct from every other identifier type, so the compiler rejects swapped
 * arguments.
 *
 * @param value the identifier value, never null or blank
 */
public record CustomerId(String value) implements EntityId {

  /**
   * Validates the value.
   *
   * @throws com.rednavis.metaldesk.share.error.ValidationException if the value is null or blank
   */
  public CustomerId {
    EntityId.requireValue(value, "CustomerId");
  }
}
