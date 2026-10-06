package com.rednavis.metaldesk.api.account.verification;

import java.time.Instant;

/**
 * An issued verification challenge (BRD FR-2.6): what a code was issued for, to whom, until when,
 * and how often it has been tried.
 *
 * <p>It never holds the code, only the encoded hash of it, and its {@code toString} omits that too.
 *
 * @param reference the opaque id the client quotes when confirming
 * @param purpose what the challenge is for
 * @param subject who it is bound to (a customer id), or null until bound
 * @param email the address the code was sent to
 * @param codeHash the encoded hash of the code
 * @param expiresAt when it stops being usable
 * @param attempts how many confirmations have been attempted
 * @param status where the challenge stands
 */
public record VerificationChallenge(
    String reference,
    VerificationPurpose purpose,
    String subject,
    String email,
    String codeHash,
    Instant expiresAt,
    int attempts,
    Status status) {

  /** Where a challenge stands. */
  public enum Status {
    /** Issued and not yet used. */
    PENDING,
    /** Confirmed with the right code; a challenge is single-use. */
    CONFIRMED,
    /** Replaced or cancelled, for example by a newer challenge or a completed reset. */
    INVALIDATED
  }

  /** Omits the hash, so a logged challenge does not leak it. */
  @Override
  public String toString() {
    return "VerificationChallenge[purpose=" + purpose + ", status=" + status + ']';
  }
}
