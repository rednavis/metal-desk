package com.rednavis.metaldesk.persistence.document;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A cart as stored.
 *
 * <p>The id is an opaque random reference that works as a capability: whoever holds it can read the
 * cart. A cart has an owner only once a customer signs in with it; the unique sparse index on
 * {@code ownerId} means a customer has at most one, and carts without an owner are not constrained.
 * {@code version} makes every change a compare-and-set, so concurrent changes cannot overwrite each
 * other.
 *
 * @param id the cart reference
 * @param ownerId the customer id, or null for an anonymous cart
 * @param lines the lines, at most one per product
 * @param version the change counter used for compare-and-set
 * @param createdAt when the cart was created
 * @param updatedAt when the cart last changed
 */
@Document("carts")
public record CartDocument(
    @Id String id,
    @Indexed(unique = true, sparse = true) String ownerId,
    List<CartLineDocument> lines,
    long version,
    Instant createdAt,
    Instant updatedAt) {

  /** Copies the list, so the document cannot be changed through it; an absent list is empty. */
  public CartDocument {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
