package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;

/**
 * A delivery quote as stored, embedded in an order.
 *
 * @param tierId the id of the tier that priced the order
 * @param cost the delivery cost
 * @param minDays the fewest days in transit
 * @param maxDays the most days in transit
 * @param quotedAt when the quote was made
 */
public record QuoteDocument(
    String tierId, MoneyDocument cost, int minDays, int maxDays, Instant quotedAt) {}
