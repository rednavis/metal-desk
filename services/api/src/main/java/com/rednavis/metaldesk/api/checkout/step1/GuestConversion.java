package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.Optional;

/**
 * What starting a guest's quick registration produced.
 *
 * @param reference the verification reference the guest quotes with the emailed code; always
 *     present and always in the same shape, whether or not an account was created
 * @param customer the account created for the guest, empty if the address already had one
 */
public record GuestConversion(String reference, Optional<CustomerId> customer) {}
