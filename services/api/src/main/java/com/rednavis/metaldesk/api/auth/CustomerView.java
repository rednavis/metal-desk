package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;

/**
 * Who the bearer token says the caller is.
 *
 * @param customerId the customer's id
 * @param verification whether the customer has verified their contact details
 */
public record CustomerView(String customerId, VerificationState verification) {}
