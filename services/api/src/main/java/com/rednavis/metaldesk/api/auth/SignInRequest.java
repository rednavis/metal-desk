package com.rednavis.metaldesk.api.auth;

/**
 * The body of a sign-in request (BRD FR-2.1). Either field may be missing; a missing one fails
 * exactly as a wrong one does (FR-2.2).
 *
 * @param identifier the customer's email address or phone number
 * @param password the plaintext password
 */
public record SignInRequest(String identifier, String password) {

  /** Omits the password, so a logged request does not leak it. */
  @Override
  public String toString() {
    return "SignInRequest[identifier=" + identifier + ", password=***]";
  }
}
