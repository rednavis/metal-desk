package com.rednavis.metaldesk.persistence.document;

/**
 * An amount of money as stored: the decimal text keeps the currency's scale exactly.
 *
 * @param amount the decimal amount, for example {@code "1959.32"}
 * @param currency the currency code
 */
public record MoneyDocument(String amount, String currency) {}
