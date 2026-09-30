package com.rednavis.metaldesk.api.cart.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * The running total of a cart by BRD BR-5, with no delivery component yet.
 *
 * @param net the sum of the lines before tax
 * @param tax the sum of the line taxes
 * @param total net plus tax
 */
public record TotalsView(PriceView net, PriceView tax, PriceView total) {}
