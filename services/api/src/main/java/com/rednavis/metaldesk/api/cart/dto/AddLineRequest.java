package com.rednavis.metaldesk.api.cart.dto;

/**
 * Adds one unit of a product to the cart, or starts a buy-now (BRD FR-3.1, FR-3.2).
 *
 * @param productId the product's id
 */
public record AddLineRequest(String productId) {}
