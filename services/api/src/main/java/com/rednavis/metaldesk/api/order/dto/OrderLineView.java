package com.rednavis.metaldesk.api.order.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * A line of an order as it was snapshotted when the order was created.
 *
 * @param productName the product's name at that time
 * @param quantity how many
 * @param unitPrice the price of one at that time
 * @param lineNet the line's net amount
 * @param taxRatePercent the tax rate that applied, as decimal text
 * @param lineTax the line's tax
 */
public record OrderLineView(
    String productName,
    int quantity,
    PriceView unitPrice,
    PriceView lineNet,
    String taxRatePercent,
    PriceView lineTax) {}
