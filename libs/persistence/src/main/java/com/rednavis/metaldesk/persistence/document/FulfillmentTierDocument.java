package com.rednavis.metaldesk.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A fulfillment tier as stored. Tiers are data, not code (Architecture section 3): staff configure
 * them, and checkout evaluates whatever is stored.
 *
 * <p>{@code region} is indexed and not unique, because a region may have several tiers (BRD
 * FR-5.1).
 *
 * @param id the tier id
 * @param region the region code the tier serves, indexed
 * @param valueCeiling the highest ex-tax order value the tier accepts
 * @param weightCeiling the highest order weight the tier accepts
 * @param deliveryPrice what the tier charges
 * @param minDays the fewest days in transit
 * @param maxDays the most days in transit
 */
@Document("fulfillment_tiers")
public record FulfillmentTierDocument(
    @Id String id,
    @Indexed String region,
    MoneyDocument valueCeiling,
    WeightDocument weightCeiling,
    MoneyDocument deliveryPrice,
    int minDays,
    int maxDays) {}
