package com.rednavis.metaldesk.api.inquiry.dto;

import com.rednavis.metaldesk.api.inquiry.InquirySource;

/**
 * What a visitor or customer sends to ask staff something.
 *
 * @param source where the inquiry comes from
 * @param name the sender's name
 * @param email the sender's email, which the answer goes to
 * @param topic what it is about
 * @param message the message
 * @param productId the product asked about; required for {@code PRODUCT}
 * @param handoffReference the order number of the handed-off order; required for {@code HANDOFF}
 * @param locale the sender's language tag, English if absent
 */
public record InquiryRequest(
    InquirySource source,
    String name,
    String email,
    String topic,
    String message,
    String productId,
    String handoffReference,
    String locale) {}
