package com.rednavis.metaldesk.share.domain.customer;

/**
 * Whether a customer has proved the email address on the account is theirs.
 *
 * <p>BRD FR-2.3 makes a newly registered account unusable for checkout until an email verification
 * code has been confirmed. That rule lives here, as {@link #canCheckout()}, so the checkout flow
 * asks the type instead of re-deriving it. How a code is issued and confirmed (FR-2.6) is not
 * modelled here — only the resulting state.
 */
public enum VerificationState {

  /** The email address has not been confirmed yet. */
  UNVERIFIED,

  /** The customer confirmed the email address with a verification code. */
  VERIFIED;

  /**
   * Tells whether an account in this state may proceed through checkout (BRD FR-2.3).
   *
   * @return {@code true} only for {@link #VERIFIED}
   */
  public boolean canCheckout() {
    return this == VERIFIED;
  }
}
