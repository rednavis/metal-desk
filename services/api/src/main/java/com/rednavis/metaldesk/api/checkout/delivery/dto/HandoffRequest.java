package com.rednavis.metaldesk.api.checkout.delivery.dto;

/**
 * Asks to hand the order to staff (BRD FR-5.3).
 *
 * @param locale the language tag for the customer's confirmation mail; optional
 */
public record HandoffRequest(String locale) {}
