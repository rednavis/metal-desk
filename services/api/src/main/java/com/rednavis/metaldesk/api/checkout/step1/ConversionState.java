package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.Optional;

/**
 * Where a guest's quick registration stands (BRD FR-4.2).
 *
 * <p>The account is created {@code UNVERIFIED} and checkout carries on: nothing waits for the
 * confirmation. {@code customer} is empty when the address already had an account, in which case no
 * account was created, nothing was attached to this session, and the response looks the same as a
 * new registration would.
 *
 * @param email the address the verification was requested for
 * @param reference the verification reference the client quotes when confirming
 * @param customer the account created for this guest, if one was
 * @param verified whether the email has been confirmed since
 */
public record ConversionState(
    String email, String reference, Optional<CustomerId> customer, boolean verified) {}
