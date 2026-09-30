package com.rednavis.metaldesk.api.persistence.document;

/**
 * The state of a guest's quick registration on a checkout session (BRD FR-4.2).
 *
 * @param email the address the verification was requested for
 * @param reference the verification reference, quoted when confirming
 * @param customerId the id of the account created for this guest, or null when none was (the
 *     address already had an account)
 * @param verified whether the email has been confirmed since
 */
public record ConversionDocument(
    String email, String reference, String customerId, boolean verified) {}
