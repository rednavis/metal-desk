package com.rednavis.metaldesk.api.cart.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import com.rednavis.metaldesk.share.domain.catalog.PricingMode;

/**
 * One line of the cart as the storefront shows it (BRD FR-3.3): identity, quantity, and the price
 * and tax as derived right now.
 *
 * <p>A line with no sellable price at the moment is {@code ON_REQUEST} and carries no price fields.
 *
 * @param productId the product's id
 * @param name the product's name
 * @param quantity the quantity in the cart
 * @param maxQuantity the most that may be ordered of one product
 * @param pricingMode {@code FIXED} when the price fields are present
 * @param unitPrice the current unit price; absent when on request
 * @param lineNet unit price times quantity, before tax; absent when on request
 * @param taxRatePercent the tax rate in percent; absent when on request
 * @param lineTax the tax on the line; absent when on request
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartLineView(
    String productId,
    String name,
    int quantity,
    int maxQuantity,
    PricingMode pricingMode,
    PriceView unitPrice,
    PriceView lineNet,
    String taxRatePercent,
    PriceView lineTax) {}
