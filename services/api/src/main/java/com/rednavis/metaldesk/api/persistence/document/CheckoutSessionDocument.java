package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A checkout session as stored.
 *
 * <p>It holds personal data (name, address, email, phone) for a checkout that may never finish, so
 * it expires: the {@code expiresAt} index deletes it when it is due, and every change pushes that
 * date out.
 *
 * @param id the session's opaque reference
 * @param ownerId the signed-in customer who started it, or null for a guest
 * @param source {@code CART} or {@code BUY_NOW}
 * @param cartId the cart it was started from, or null
 * @param lines the basket lines as quoted when the session started
 * @param details the step-1 customer and delivery data, or null before step 1
 * @param consent the privacy acceptance, or null before step 1
 * @param conversion the guest's quick registration, or null
 * @param version the change counter used for compare-and-set
 * @param createdAt when the session started
 * @param updatedAt when it last changed
 * @param expiresAt when it is deleted
 */
@Document("checkout_sessions")
public record CheckoutSessionDocument(
    @Id String id,
    String ownerId,
    String source,
    String cartId,
    List<OrderLineDocument> lines,
    CustomerDetailsDocument details,
    ConsentDocument consent,
    ConversionDocument conversion,
    long version,
    Instant createdAt,
    Instant updatedAt,
    @Indexed(expireAfter = "0s") Instant expiresAt) {

  /** Copies the list, so the document cannot be changed through it. */
  public CheckoutSessionDocument {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
