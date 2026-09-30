package com.rednavis.metaldesk.api.persistence.document;

/**
 * What a checkout session knows about paying, as stored.
 *
 * @param method the chosen payment method name, or null
 * @param orderId the order created for this checkout, or null
 * @param reference the provider reference awaiting confirmation, or null
 * @param phase the payment phase name
 */
public record SessionPaymentDocument(
    String method, String orderId, String reference, String phase) {}
