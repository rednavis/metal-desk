package com.rednavis.metaldesk.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.PricingMode;
import com.rednavis.metaldesk.share.domain.catalog.StockStatus;

/**
 * The detail page of one item, with every field BRD FR-1.3 asks for: purity, weight and size, stock
 * status, price and tax treatment.
 *
 * @param id the item's id
 * @param name the item's display name
 * @param categoryId the id of its category
 * @param categoryName its category's name
 * @param metal the metal it is made of
 * @param purity the purity in parts per thousand, as decimal text
 * @param weight its weight
 * @param dimensions its size as free text; absent when none is recorded
 * @param stock its availability
 * @param pricingMode {@code FIXED} when a price is shown, {@code ON_REQUEST} for a "request price"
 * @param price the current sellable unit price; absent when {@code pricingMode} is {@code
 *     ON_REQUEST}
 * @param tax how it is taxed
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductDetailView(
    String id,
    String name,
    String categoryId,
    String categoryName,
    Metal metal,
    String purity,
    WeightView weight,
    String dimensions,
    StockStatus stock,
    PricingMode pricingMode,
    PriceView price,
    TaxTreatmentView tax) {}
