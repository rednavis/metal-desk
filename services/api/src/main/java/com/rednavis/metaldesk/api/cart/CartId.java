package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * A cart's identity: an opaque random reference that exists before anyone signs in and survives
 * after they sign out. It is not a {@code libs/share} identifier because a cart is not a shared
 * aggregate.
 *
 * @param value the reference, never blank
 */
public record CartId(String value) {

  /**
   * Validates the reference.
   *
   * @throws ValidationException if it is null or blank
   */
  public CartId {
    if (value == null || value.isBlank()) {
      throw new ValidationException("cart.id-blank", "Cart id must not be null or blank");
    }
  }
}
