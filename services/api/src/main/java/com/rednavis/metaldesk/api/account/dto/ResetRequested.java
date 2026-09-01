package com.rednavis.metaldesk.api.account.dto;

/**
 * The answer to a reset request. It is identical whether or not the address belongs to an account,
 * so it cannot be used to find out which addresses are registered.
 *
 * @param message what the client should tell the user
 */
public record ResetRequested(String message) {}
