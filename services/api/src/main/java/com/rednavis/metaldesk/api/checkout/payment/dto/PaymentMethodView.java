package com.rednavis.metaldesk.api.checkout.payment.dto;

/**
 * One payment method on offer.
 *
 * @param method the method's name
 * @param group {@code GATEWAY}, {@code WALLET} or {@code INVOICE}
 */
public record PaymentMethodView(String method, String group) {}
