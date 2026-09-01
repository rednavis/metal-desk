package com.rednavis.metaldesk.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The record that one notification was sent for one order. Its id is the order number and the
 * template, so claiming a notification is an insert that a duplicate cannot win twice.
 *
 * @param id the order number and the template name
 * @param orderId the order's id
 * @param template the template's name
 * @param claimedAt when the send was claimed
 */
@Document("order_notifications")
public record NotificationDocument(
    @Id String id, String orderId, String template, Instant claimedAt) {}
