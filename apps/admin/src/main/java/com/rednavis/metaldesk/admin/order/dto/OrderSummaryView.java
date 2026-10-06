package com.rednavis.metaldesk.admin.order.dto;

import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.time.Instant;

/**
 * An order as listed.
 *
 * @param id the order id
 * @param number the order number
 * @param status the status
 * @param customerId the customer
 * @param itemCount how many items the order holds
 * @param total the order total including delivery, as decimal text
 * @param currency the currency code of the total
 * @param createdAt when the order was created
 * @param updatedAt when it last changed
 */
public record OrderSummaryView(
    String id,
    String number,
    OrderStatus status,
    String customerId,
    int itemCount,
    String total,
    String currency,
    Instant createdAt,
    Instant updatedAt) {}
