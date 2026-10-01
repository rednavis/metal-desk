package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.share.domain.user.UserRole;

/**
 * A successful sign-in.
 *
 * @param accessToken the signed bearer token
 * @param tokenType always {@code Bearer}
 * @param expiresInSeconds how long the token lives; there is no refresh token
 * @param login the signed-in user's login name
 * @param role the signed-in user's role
 */
public record SignInResponse(
    String accessToken, String tokenType, long expiresInSeconds, String login, UserRole role) {

  /** Redacts the token, so a logged response does not leak it. */
  @Override
  public String toString() {
    return "SignInResponse[login="
        + login
        + ", role="
        + role
        + ", expiresInSeconds="
        + expiresInSeconds
        + ']';
  }
}
