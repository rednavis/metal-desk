package com.rednavis.metaldesk.api.auth;

/**
 * A successful sign-in.
 *
 * @param accessToken the signed bearer token
 * @param tokenType always {@code Bearer}
 * @param expiresInSeconds how long the token lives; there is no refresh token
 */
public record SignInResponse(String accessToken, String tokenType, long expiresInSeconds) {

  /** Redacts the token, so a logged response does not leak it. */
  @Override
  public String toString() {
    return "SignInResponse[expiresInSeconds=" + expiresInSeconds + ']';
  }
}
