package com.rednavis.metaldesk.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The terms staff set on a handed-off order (BRD FR-5.3), kept apart from the order because the
 * order aggregate carries only the resulting delivery price.
 *
 * @param orderId the order the terms were set on; one set of terms per order
 * @param finalPrice the delivery price staff decided
 * @param terms the terms text the customer is shown
 * @param quotedAt when the terms were set
 * @param validUntil until when the customer may pay on them
 * @param staff who set them, as the identity-aware proxy reported it
 */
@Document("manager_quotes")
public record ManagerQuoteDocument(
    @Id String orderId,
    MoneyDocument finalPrice,
    String terms,
    Instant quotedAt,
    Instant validUntil,
    String staff) {}
