package com.rednavis.metaldesk.api.checkout.dto;

/**
 * Confirms the email address of a guest's quick registration.
 *
 * @param reference the verification reference from step 1
 * @param code the code from the email
 */
public record ConfirmEmailRequest(String reference, String code) {

  /** Omits the code, so a logged request does not leak it. */
  @Override
  public String toString() {
    return "ConfirmEmailRequest[code=***]";
  }
}
