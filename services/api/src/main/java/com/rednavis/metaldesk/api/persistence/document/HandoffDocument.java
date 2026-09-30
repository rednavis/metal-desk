package com.rednavis.metaldesk.api.persistence.document;

import java.time.Instant;

/**
 * That a session was handed to staff, as stored.
 *
 * @param reference the customer-facing reference, which is the order number
 * @param orderId the id of the order created for the handoff
 * @param at when it was handed off
 */
public record HandoffDocument(String reference, String orderId, Instant at) {}
