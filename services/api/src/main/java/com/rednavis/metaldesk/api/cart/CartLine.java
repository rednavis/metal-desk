package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * One line of a cart: which product and how many. <strong>There is no price on it</strong> and
 * there must not be: the price is derived on every read, so it can never be stale.
 *
 * @param productId the product, never null
 * @param quantity the number of units, within the shared {@link Quantity} cap, never null
 */
public record CartLine(ProductId productId, Quantity quantity) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the product or quantity is null
   */
  public CartLine {
    if (productId == null || quantity == null) {
      throw new ValidationException(
          "cart-line.field-missing", "A cart line needs a product and a quantity");
    }
  }
}
