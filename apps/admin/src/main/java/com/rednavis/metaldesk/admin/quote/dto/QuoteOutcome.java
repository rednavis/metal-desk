package com.rednavis.metaldesk.admin.quote.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;

/**
 * What became of an order after staff acted on its quote.
 *
 * @param orderId the order id
 * @param number the order number
 * @param status the status the state machine moved it to
 * @param deliveryPrice the delivery price on the order, absent after a decline
 * @param total the order total the customer will now be asked to pay, absent after a decline
 * @param currency the currency code of the amounts, absent after a decline
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuoteOutcome(
    String orderId,
    String number,
    OrderStatus status,
    String deliveryPrice,
    String total,
    String currency) {}
