package com.rednavis.metaldesk.api.account.dto;

/**
 * The minimum viable profile for registering (BRD FR-2.3).
 *
 * @param name the customer's name
 * @param email the email address, which is verified before the account can check out
 * @param phone the phone number, optional; another sign-in identifier
 * @param password the plaintext password
 * @param locale the language tag for the verification mail, for example {@code de}; optional
 */
public record RegistrationRequest(
    String name, String email, String phone, String password, String locale) {

  /** Omits the password, so a logged request does not leak it. */
  @Override
  public String toString() {
    return "RegistrationRequest[email=" + email + ", password=***]";
  }
}
