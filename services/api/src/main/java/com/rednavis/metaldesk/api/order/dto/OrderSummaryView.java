package com.rednavis.metaldesk.api.order.dto;

import com.rednavis.metaldesk.api.catalog.dto.PriceView;
import java.time.Instant;

/**
 * One row of the order history (BRD FR-10.1).
 *
 * @param orderNumber the order number
 * @param createdAt when the order was created
 * @param itemCount the number of items across all lines
 * @param total the grand total, from the order's own snapshot
 * @param status the status, by name
 * @param statusLabel the status as the customer reads it
 */
public record OrderSummaryView(
    String orderNumber,
    Instant createdAt,
    int itemCount,
    PriceView total,
    String status,
    String statusLabel) {}
