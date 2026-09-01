package com.rednavis.metaldesk.api.account.dto;

/**
 * The answer to a registration. It is the same shape whether the address was new or already known,
 * so it does not reveal which; the client should tell the user to expect an email.
 *
 * @param reference the verification reference to quote with the emailed code
 * @param message what the client should tell the user
 */
public record RegistrationAccepted(String reference, String message) {}
