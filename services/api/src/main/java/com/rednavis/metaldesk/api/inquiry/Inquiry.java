package com.rednavis.metaldesk.api.inquiry;

import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * A customer's inquiry or message to staff (BRD FR-9.1, FR-9.2). The sender need not be signed in,
 * so the sender is a name and a validated email address, not a customer.
 *
 * @param reference the reference the customer quotes
 * @param source where it came from
 * @param name the sender's name
 * @param email the sender's email
 * @param topic what it is about
 * @param message the message
 * @param productId the product asked about, for a product inquiry
 * @param orderNumber the order handed to a manager, for a handoff inquiry
 * @param locale the sender's language
 * @param createdAt when it was received
 */
public record Inquiry(
    String reference,
    InquirySource source,
    String name,
    EmailAddress email,
    String topic,
    String message,
    Optional<String> productId,
    Optional<String> orderNumber,
    Locale locale,
    Instant createdAt) {}
