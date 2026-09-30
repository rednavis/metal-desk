package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;

/**
 * The acceptance of the privacy policy (BRD FR-4.3): that it was accepted, which version, and when.
 *
 * <p>It is one consent, with its own type. There is deliberately no generic flag that stands for
 * several consents, and one cannot be constructed with {@code privacyPolicyAccepted} false: a
 * customer who has not accepted has no record, and step 1 does not advance.
 *
 * @param privacyPolicyAccepted always true; a record of a refusal does not exist
 * @param policyVersion the version of the policy that was accepted, never blank
 * @param acceptedAt when it was accepted, never null
 */
public record ConsentRecord(
    boolean privacyPolicyAccepted, String policyVersion, Instant acceptedAt) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the policy was not accepted, or the version or time is missing
   */
  public ConsentRecord {
    if (!privacyPolicyAccepted) {
      throw new ValidationException(
          "consent.privacy-required", "The privacy policy must be accepted");
    }
    if (policyVersion == null || policyVersion.isBlank() || acceptedAt == null) {
      throw new ValidationException(
          "consent.incomplete", "A consent needs the accepted policy version and a time");
    }
  }
}
