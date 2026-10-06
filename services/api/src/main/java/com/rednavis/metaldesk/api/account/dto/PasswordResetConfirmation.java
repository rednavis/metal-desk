package com.rednavis.metaldesk.api.account.dto;

/**
 * Completes a password reset with the parts of the emailed link.
 *
 * @param reference the reference from the link
 * @param code the code from the link
 * @param newPassword the plaintext new password
 */
public record PasswordResetConfirmation(String reference, String code, String newPassword) {

  /** Omits the code and password, so a logged request does not leak them. */
  @Override
  public String toString() {
    return "PasswordResetConfirmation[code=***, newPassword=***]";
  }
}
