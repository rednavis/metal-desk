package com.rednavis.metaldesk.admin.tier.dto;

import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;

/**
 * A fulfillment tier as staff describe it (BRD FR-5.1). Amounts are decimal text, as everywhere in
 * the API.
 *
 * @param region the region code the tier applies to
 * @param valueCeiling the highest order value, before tax, the tier prices automatically
 * @param weightCeiling the heaviest order the tier prices automatically
 * @param weightUnit the unit of {@code weightCeiling}
 * @param currency the currency of {@code valueCeiling} and {@code deliveryPrice}
 * @param deliveryPrice what delivery costs on this tier, never negative
 * @param minDays the fewest days delivery takes, at least one
 * @param maxDays the most days delivery takes, not below {@code minDays}
 */
public record TierRequest(
    String region,
    String valueCeiling,
    String weightCeiling,
    WeightUnit weightUnit,
    Currency currency,
    String deliveryPrice,
    int minDays,
    int maxDays) {}
