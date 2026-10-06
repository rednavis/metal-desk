package com.rednavis.metaldesk.admin.tier.dto;

/**
 * A configured tier.
 *
 * @param id the tier id
 * @param region the region code
 * @param valueCeiling the value ceiling, as decimal text
 * @param currency the currency code
 * @param weightGrams the weight ceiling in grams, as decimal text
 * @param deliveryPrice the delivery price, as decimal text
 * @param minDays the fewest delivery days
 * @param maxDays the most delivery days
 */
public record TierView(
    String id,
    String region,
    String valueCeiling,
    String currency,
    String weightGrams,
    String deliveryPrice,
    int minDays,
    int maxDays) {}
