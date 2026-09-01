package com.rednavis.metaldesk.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.PricingMode;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;

/**
 * A catalog entry, as listed and searched (BRD FR-1.2).
 *
 * <p>The client tells a priced entry from an unpriced one by {@code pricingMode}, never by looking
 * at {@code price}: an {@code ON_REQUEST} entry has no {@code price} field at all, not a zero one.
 *
 * @param id the item's id
 * @param name the item's display name
 * @param categoryId the id of the category it is listed under
 * @param metal the metal it is made of
 * @param stock its availability
 * @param pricingMode {@code FIXED} when a price is shown, {@code ON_REQUEST} for a "request price"
 * @param price the current sellable unit price; absent when {@code pricingMode} is {@code
 *     ON_REQUEST}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductSummaryView(
    String id,
    String name,
    String categoryId,
    Metal metal,
    StockStatus stock,
    PricingMode pricingMode,
    PriceView price) {}
