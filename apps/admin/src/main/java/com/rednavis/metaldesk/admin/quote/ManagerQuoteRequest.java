package com.rednavis.metaldesk.admin.quote;

import java.time.Instant;

/**
 * What staff decide for a handed-off order (BRD FR-5.3).
 *
 * <p>The BRD does not say whether the final price is the whole order or delivery alone; it is taken
 * as the <em>delivery</em> price, since the goods are already priced and the order total is then
 * the lines plus this figure.
 *
 * @param deliveryPrice the final delivery price, as decimal text, above zero
 * @param terms the terms shown to the customer, not blank, at most 2000 characters
 * @param transitMinDays the fewest days delivery takes, at least one
 * @param transitMaxDays the most days delivery takes, not below the minimum
 * @param validUntil until when the customer may pay on these terms, in the future
 */
public record ManagerQuoteRequest(
    String deliveryPrice,
    String terms,
    int transitMinDays,
    int transitMaxDays,
    Instant validUntil) {}
