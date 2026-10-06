package com.rednavis.metaldesk.api.account.dto;

/**
 * Confirms an email address (BRD FR-2.6).
 *
 * @param reference the reference returned by registration
 * @param code the code from the email
 */
public record ConfirmEmailRequest(String reference, String code) {

  /** Omits the code, so a logged request does not leak it. */
  @Override
  public String toString() {
    return "ConfirmEmailRequest[code=***]";
  }
}
