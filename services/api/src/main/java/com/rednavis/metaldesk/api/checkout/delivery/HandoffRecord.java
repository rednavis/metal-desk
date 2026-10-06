package com.rednavis.metaldesk.api.checkout.delivery;

import com.rednavis.metaldesk.share.domain.id.OrderId;
import java.time.Instant;

/**
 * That a session was handed to staff, and under what reference.
 *
 * <p><strong>The handoff reference is the order number</strong> (BRD BR-6, twelve digits). The
 * order is created at handoff, because the state machine reaches {@code AWAITING_MANAGER_QUOTE}
 * only from an order awaiting payment, so an order number exists from that moment and a second
 * identifier for the same thing would only give the customer two numbers to quote. Staff find the
 * order by it.
 *
 * @param reference the customer-facing reference, which is the formatted order number
 * @param orderId the id of the order created for the handoff
 * @param at when it was handed off
 */
public record HandoffRecord(String reference, OrderId orderId, Instant at) {}
