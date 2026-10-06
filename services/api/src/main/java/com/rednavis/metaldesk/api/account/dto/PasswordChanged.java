package com.rednavis.metaldesk.api.account.dto;

/**
 * The answer to a completed password reset.
 *
 * @param changed always true; a failure is an error response
 */
public record PasswordChanged(boolean changed) {}
