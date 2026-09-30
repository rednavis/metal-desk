package com.rednavis.metaldesk.api.account.dto;

/**
 * Asks for a password-reset link (BRD FR-2.4).
 *
 * @param email the account's email address
 * @param locale the language tag for the mail; optional
 */
public record PasswordResetRequest(String email, String locale) {}
