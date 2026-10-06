package com.rednavis.metaldesk.api.cart;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.ProductId;
import com.rednavis.metaldesk.share.domain.order.Quantity;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A shopper's cart: a list of products and quantities, at most one line per product.
 *
 * <p>It is immutable; every change returns a new cart, and returns the <em>same</em> cart when the
 * change would have no effect, so callers can tell.
 *
 * <p><strong>Semantics that the BRD leaves open, and the choices made.</strong> FR-3.1 says a
 * repeat click on add-to-cart "does not duplicate the line"; it does not say whether the quantity
 * goes up. FR-3.3 makes quantity separately editable, so <em>adding an item that is already in the
 * cart is a no-op</em>: the customer changes the quantity on the line. (Incrementing would change
 * what a double-click buys.) Removing a line that is not there is likewise a no-op, not an error.
 * An over-cap quantity is refused, never clamped, because a silent clamp tells the customer they
 * bought ten when they asked for twelve; only {@link #mergedWith merging} caps, since no one asked
 * for the sum.
 *
 * @param id the cart's reference
 * @param owner the customer who owns it, or empty while it is anonymous
 * @param lines the lines, never null
 * @param version the change counter used for compare-and-set
 * @param createdAt when it was created
 * @param updatedAt when it last changed
 */
public record Cart(
    CartId id,
    Optional<CustomerId> owner,
    List<CartLine> lines,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  /**
   * Validates the cart and copies the lines.
   *
   * @throws ValidationException if a field is missing or two lines are for the same product
   */
  public Cart {
    if (id == null || owner == null || lines == null || createdAt == null || updatedAt == null) {
      throw new ValidationException(
          "cart.field-missing", "A cart needs an id, owner, lines and times");
    }
    lines = List.copyOf(lines);
    if (lines.stream().map(CartLine::productId).distinct().count() != lines.size()) {
      throw new ValidationException("cart.duplicate-line", "A cart has one line per product");
    }
  }

  /**
   * Adds one unit of a product, unless the cart already has a line for it.
   *
   * @param productId the product
   * @return a cart with a line for it; this same cart if it already had one
   */
  public Cart addIfAbsent(ProductId productId) {
    return has(productId) ? this : withLines(append(new CartLine(productId, Quantity.of(1))));
  }

  /**
   * Sets the quantity of an existing line.
   *
   * @param productId the product
   * @param quantity the new quantity, within the cap
   * @return the changed cart
   * @throws NotFoundException with code {@code cart.line-not-found} if there is no such line
   */
  public Cart withQuantity(ProductId productId, Quantity quantity) {
    if (!has(productId)) {
      throw new NotFoundException("cart.line-not-found", "The cart has no line for that product");
    }
    return withLines(
        lines.stream()
            .map(
                line ->
                    line.productId().equals(productId) ? new CartLine(productId, quantity) : line)
            .toList());
  }

  /**
   * Removes the line for a product.
   *
   * @param productId the product
   * @return the changed cart; this same cart if it had no such line
   */
  public Cart without(ProductId productId) {
    return has(productId)
        ? withLines(lines.stream().filter(line -> !line.productId().equals(productId)).toList())
        : this;
  }

  /**
   * Empties the cart.
   *
   * @return a cart with no lines; this same cart if it was already empty
   */
  public Cart cleared() {
    return lines.isEmpty() ? this : withLines(List.of());
  }

  /**
   * Adds another cart's lines to this one: a union by product, with the quantity of a product in
   * both the sum, capped at {@link Quantity#MAX}.
   *
   * @param other the cart to take lines from
   * @return the merged cart
   */
  public Cart mergedWith(Cart other) {
    final List<CartLine> merged = new ArrayList<>(lines);
    for (final CartLine incoming : other.lines()) {
      final int index = indexOf(merged, incoming.productId());
      if (index < 0) {
        merged.add(incoming);
      } else {
        final int sum = merged.get(index).quantity().value() + incoming.quantity().value();
        merged.set(
            index, new CartLine(incoming.productId(), Quantity.of(Math.min(sum, Quantity.MAX))));
      }
    }
    return withLines(merged);
  }

  /**
   * Counts the units in the cart.
   *
   * @return the sum of the quantities
   */
  public int itemCount() {
    return lines.stream().mapToInt(line -> line.quantity().value()).sum();
  }

  private boolean has(ProductId productId) {
    return indexOf(lines, productId) >= 0;
  }

  private static int indexOf(List<CartLine> among, ProductId productId) {
    int found = -1;
    for (int i = 0; i < among.size() && found < 0; i++) {
      if (among.get(i).productId().equals(productId)) {
        found = i;
      }
    }
    return found;
  }

  private List<CartLine> append(CartLine line) {
    final List<CartLine> extended = new ArrayList<>(lines);
    extended.add(line);
    return extended;
  }

  private Cart withLines(List<CartLine> changed) {
    return new Cart(id, owner, changed, version, createdAt, updatedAt);
  }
}
