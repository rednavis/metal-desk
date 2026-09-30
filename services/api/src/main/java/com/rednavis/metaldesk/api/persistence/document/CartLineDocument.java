package com.rednavis.metaldesk.api.persistence.document;

/**
 * One cart line as stored: which product and how many, and nothing else. There is deliberately no
 * price here; a cart's prices are derived on every read (BRD BR-2 applies to orders, not carts).
 *
 * @param productId the product's id
 * @param quantity the number of units
 */
public record CartLineDocument(String productId, int quantity) {}
