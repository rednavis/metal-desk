package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The one error every failed confirmation becomes, whatever the real reason: a wrong code, an
 * expired or used challenge, no attempts left, a wrong purpose. The reason is logged by the
 * verification service and never reaches the client.
 */
public final class VerificationFailure {

  /** The single error code of a failed confirmation. */
  public static final String CODE = "verification.invalid";

  private VerificationFailure() {}

  /**
   * Builds the error.
   *
   * @return the validation error to signal
   */
  public static ValidationException create() {
    return new ValidationException(CODE, "The code is not valid or has expired");
  }
}
