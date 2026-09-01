package com.rednavis.metaldesk.share.domain.id;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The contract every typed identifier satisfies: it wraps exactly one non-blank string.
 *
 * <p>This is a {@code sealed} interface rather than an abstract record component contract because
 * records cannot extend a class, and because a closed set lets a {@code switch} over identifiers be
 * exhaustive. It is also why the five identifiers are not assignment-compatible: each is a separate
 * final record, so {@code orderFor(customerId, productId)} cannot be called with its arguments
 * swapped.
 *
 * <p>There is intentionally no {@code random()} factory: generating an identifier belongs to
 * whatever persists the aggregate (order numbering has its own format, BRD BR-6).
 */
public sealed interface EntityId
    permits CustomerId, ProductId, OrderId, CategoryId, FulfillmentTierId {

  /**
   * Returns the identifier's raw string value.
   *
   * @return the value, never blank
   */
  String value();

  /**
   * Validates an identifier value on behalf of the implementing records.
   *
   * @param value the candidate value
   * @param type the identifier's type name, used in the error message
   * @return the value unchanged
   * @throws ValidationException if the value is null or blank
   */
  static String requireValue(String value, String type) {
    if (value == null || value.isBlank()) {
      throw new ValidationException("id.blank", type + " must not be null or blank");
    }
    return value;
  }
}
