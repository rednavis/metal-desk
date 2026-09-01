package com.rednavis.metaldesk.api.checkout.dto;

/**
 * The guest's quick registration as the client sees it (BRD FR-4.2). It is the same whether the
 * address was new or already had an account, so it does not reveal which.
 *
 * <p>Checkout does not wait for the confirmation: the guest carries on, and confirms the emailed
 * code whenever they like.
 *
 * @param reference the reference to quote with the emailed code
 * @param verified whether the email has been confirmed
 */
public record ConversionView(String reference, boolean verified) {}
