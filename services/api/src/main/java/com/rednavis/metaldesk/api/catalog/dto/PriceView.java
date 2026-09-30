package com.rednavis.metaldesk.api.catalog.dto;

/**
 * A price as shown to the storefront.
 *
 * @param amount the amount as decimal text at the currency's scale, for example {@code "1959.32"}
 * @param currency the currency code
 */
public record PriceView(String amount, String currency) {}
