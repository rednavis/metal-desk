package com.rednavis.metaldesk.api.checkout.delivery.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import java.time.Instant;

/**
 * The automatic delivery quote (BRD FR-5.2). The cost has insurance folded in (BR-7, FR-5.4); there
 * is no separate insurance line.
 *
 * @param tierId the tier that priced the delivery
 * @param cost the delivery cost
 * @param minDays the fewest days in transit
 * @param maxDays the most days in transit
 * @param quotedAt when it was quoted
 */
public record QuoteView(
    String tierId, PriceView cost, int minDays, int maxDays, Instant quotedAt) {}
