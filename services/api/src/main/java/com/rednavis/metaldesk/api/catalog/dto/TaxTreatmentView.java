package com.rednavis.metaldesk.api.catalog.dto;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;

/**
 * How an item is taxed (BRD FR-1.3, BR-4).
 *
 * @param category the tax classification
 * @param ratePercent the rate in percent, as decimal text; {@code "0"} for a zero-rated item
 */
public record TaxTreatmentView(TaxCategory category, String ratePercent) {}
