package com.rednavis.metaldesk.api.checkout.payment.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * The totals of the final overview by BRD BR-5: {@code Σ(unit price × quantity) + tax + delivery}.
 *
 * @param net the sum of the lines before tax
 * @param tax the sum of the line taxes
 * @param delivery the delivery cost, insurance included (BR-7)
 * @param grandTotal net plus tax plus delivery
 */
public record OverviewTotalsView(
    PriceView net, PriceView tax, PriceView delivery, PriceView grandTotal) {}
