package com.rednavis.metaldesk.api.auth;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;

/**
 * Who a request is from, once its token has been validated: the customer and whether their email is
 * verified. Nothing else is in the token, so nothing else is known here.
 *
 * <p>Whether the customer may check out (BRD FR-2.3) is read from {@code verification} by the
 * checkout task (T-035); sign-in itself never depends on it.
 *
 * @param id the customer's id
 * @param verification whether the customer has verified their contact details
 */
public record AuthenticatedCustomer(CustomerId id, VerificationState verification) {}
