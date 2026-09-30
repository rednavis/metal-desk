package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.api.persistence.document.CartDocument;
import com.rednavis.metaldesk.api.persistence.document.CartLineDocument;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import java.util.Optional;

/** Maps between a stored cart and a {@link Cart}. */
public final class CartMapping {

  private CartMapping() {}

  /**
   * Builds the stored form of a cart line.
   *
   * @param line the line
   * @return the document
   */
  public static CartLineDocument toDocument(CartLine line) {
    return new CartLineDocument(line.productId().value(), line.quantity().value());
  }

  /**
   * Rebuilds a cart.
   *
   * @param document the stored cart
   * @return the cart
   */
  public static Cart toCart(CartDocument document) {
    return new Cart(
        new CartId(document.id()),
        Optional.ofNullable(document.ownerId()).map(CustomerId::new),
        document.lines().stream()
            .map(
                line ->
                    new CartLine(new ProductId(line.productId()), new Quantity(line.quantity())))
            .toList(),
        document.version(),
        document.createdAt(),
        document.updatedAt());
  }
}
