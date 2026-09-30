package com.rednavis.metaldesk.persistence.document;

import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An order as stored.
 *
 * <p>The unique index on {@code number} is the second half of BRD BR-6's guarantee: {@code
 * OrderNumberSequence} hands out distinct numbers, and this index makes a duplicate fail even if a
 * caller bypasses it. {@code customerId} is indexed for an account's order history.
 *
 * @param id the order id
 * @param number the twelve-digit order number, unique
 * @param customerId the id of the customer who placed it
 * @param deliveryAddress where it is delivered
 * @param lines the order lines
 * @param quote the delivery quote, or null before one exists
 * @param payment the payment record, or null before one exists
 * @param status where the order is in its lifecycle
 * @param createdAt when the order was created
 * @param updatedAt when the order last changed
 */
@Document("orders")
public record OrderDocument(
    @Id String id,
    @Indexed(unique = true) String number,
    @Indexed String customerId,
    AddressDocument deliveryAddress,
    List<OrderLineDocument> lines,
    QuoteDocument quote,
    PaymentDocument payment,
    OrderStatus status,
    Instant createdAt,
    Instant updatedAt) {

  /** Copies the list, so the document cannot be changed through it; an absent list is empty. */
  public OrderDocument {
    lines = lines == null ? List.of() : List.copyOf(lines);
  }
}
