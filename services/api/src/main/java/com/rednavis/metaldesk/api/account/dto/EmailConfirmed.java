package com.rednavis.metaldesk.api.account.dto;

/**
 * The answer to a successful email confirmation.
 *
 * @param verified always true; a failure is an error response, not a false here
 */
public record EmailConfirmed(boolean verified) {}
