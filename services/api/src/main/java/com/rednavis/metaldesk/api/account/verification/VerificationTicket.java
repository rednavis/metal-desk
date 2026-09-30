package com.rednavis.metaldesk.api.account.verification;

/**
 * The receipt for an issued challenge: the reference to quote when confirming. The code is never
 * part of it; it goes only to the mailbox.
 *
 * @param reference the opaque challenge reference
 */
public record VerificationTicket(String reference) {}
