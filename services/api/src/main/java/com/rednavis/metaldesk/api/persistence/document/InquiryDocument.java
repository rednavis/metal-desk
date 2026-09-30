package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer's inquiry or message to staff.
 *
 * @param id the document id
 * @param reference the reference the customer quotes
 * @param source where the inquiry came from
 * @param name the sender's name
 * @param email the sender's email
 * @param topic what it is about
 * @param message the message
 * @param productId the product asked about, for a product inquiry
 * @param orderNumber the order handed to a manager, for a handoff inquiry
 * @param locale the language of the sender
 * @param createdAt when it was received
 */
@Document("inquiries")
public record InquiryDocument(
    @Id String id,
    @Indexed(unique = true) String reference,
    String source,
    String name,
    String email,
    String topic,
    String message,
    String productId,
    String orderNumber,
    String locale,
    Instant createdAt) {}
