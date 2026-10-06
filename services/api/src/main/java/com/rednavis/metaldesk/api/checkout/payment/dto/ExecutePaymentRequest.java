package com.rednavis.metaldesk.api.checkout.payment.dto;

/**
 * Pays for the order (BRD FR-7.2).
 *
 * <p>{@code confirmedTotal} is the grand total the customer saw on the overview. It is required so
 * that the customer is never charged an amount they were not shown: if the total has moved since (a
 * spot price changed before the order was created), the payment is refused with 409 and the
 * customer reviews the new overview.
 *
 * @param confirmedTotal the grand total the customer confirmed, as decimal text
 * @param locale the language tag for the customer's invoice and payment pages; optional
 */
public record ExecutePaymentRequest(String confirmedTotal, String locale) {}
