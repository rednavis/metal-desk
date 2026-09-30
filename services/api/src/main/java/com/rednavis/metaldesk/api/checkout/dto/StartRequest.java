package com.rednavis.metaldesk.api.checkout.dto;

/**
 * Starts a checkout. Empty means "from my cart"; a product id means buy-now of one unit, which
 * skips the cart (BRD FR-3.2).
 *
 * @param buyNowProductId the product to buy now, or null to check out the cart
 */
public record StartRequest(String buyNowProductId) {}
