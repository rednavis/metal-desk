package com.rednavis.metaldesk.admin.security;

/**
 * What a user submits to sign in. Either field may be null; that is just a failed sign-in.
 *
 * @param login the login name
 * @param password the plaintext password
 */
public record SignInRequest(String login, String password) {

  /** Redacts the password, so a logged request does not leak it. */
  @Override
  public String toString() {
    return "SignInRequest[login=" + login + ", password=***]";
  }
}
