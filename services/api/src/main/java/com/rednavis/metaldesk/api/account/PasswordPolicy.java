package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.nio.charset.StandardCharsets;

/**
 * The rule a new password must meet: at least {@value #MIN_LENGTH} characters and at most {@value
 * #MAX_BYTES} bytes, which is all the password hash reads. The BRD sets no composition rule, so
 * none is invented.
 */
public final class PasswordPolicy {

  /** The fewest characters a password may have. */
  public static final int MIN_LENGTH = 8;

  /** The most bytes a password may have: what BCrypt can use. */
  public static final int MAX_BYTES = 72;

  private PasswordPolicy() {}

  /**
   * Checks a new password.
   *
   * @param password the plaintext password
   * @return the same password, if acceptable
   * @throws ValidationException with code {@code password.invalid} if it is not
   */
  public static String require(String password) {
    if (password == null
        || password.length() < MIN_LENGTH
        || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new ValidationException(
          "password.invalid",
          "Password must be at least "
              + MIN_LENGTH
              + " characters and at most "
              + MAX_BYTES
              + " bytes");
    }
    return password;
  }
}
