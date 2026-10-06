package com.rednavis.metaldesk.api.account.verification;

/**
 * How a confirmation ended, as one type so that callers cannot tell failures apart (BRD FR-2.2's
 * reasoning applied to codes): a wrong code, an expired challenge, one already used and one out of
 * attempts are all {@link Failed}. The real reason is logged server-side only.
 */
public sealed interface VerificationOutcome {

  /**
   * The code was right.
   *
   * @param purpose what the challenge was for
   * @param subject who it was bound to, or null if it was never bound
   * @param email the address the code was sent to
   */
  record Confirmed(VerificationPurpose purpose, String subject, String email)
      implements VerificationOutcome {}

  /** The confirmation failed, for any reason. */
  record Failed() implements VerificationOutcome {}
}
