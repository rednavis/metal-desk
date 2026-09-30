package com.rednavis.metaldesk.api.order.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;

/**
 * The amounts of an order, as they were when it was created.
 *
 * @param net the net amount
 * @param tax the tax
 * @param delivery the delivery cost
 * @param grandTotal the grand total
 */
public record OrderTotalsView(
    PriceView net, PriceView tax, PriceView delivery, PriceView grandTotal) {}
