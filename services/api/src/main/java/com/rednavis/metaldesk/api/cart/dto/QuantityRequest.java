package com.rednavis.metaldesk.api.cart.dto;

/**
 * Sets the quantity of an existing cart line (BRD FR-3.3).
 *
 * @param quantity the new quantity; refused, never clamped, if above the cap
 */
public record QuantityRequest(int quantity) {}
