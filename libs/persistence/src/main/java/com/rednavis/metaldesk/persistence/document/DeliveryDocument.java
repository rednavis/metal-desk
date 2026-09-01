package com.rednavis.metaldesk.persistence.document;

import java.time.Instant;

/**
 * The outcome of a session's last delivery evaluation, as stored.
 *
 * @param stage {@code PAYMENT_ALLOWED} or {@code HANDOFF_REQUIRED}
 * @param quote the automatic quote, or null when a manager is needed
 * @param reason the handoff reason, or null when payment is allowed
 * @param exTaxValue the order value before tax
 * @param weight the total weight
 * @param evaluatedAt when the tiers were evaluated
 * @param handoff the manager handoff of this session, or null
 */
public record DeliveryDocument(
    String stage,
    QuoteDocument quote,
    String reason,
    MoneyDocument exTaxValue,
    WeightDocument weight,
    Instant evaluatedAt,
    HandoffDocument handoff) {}
