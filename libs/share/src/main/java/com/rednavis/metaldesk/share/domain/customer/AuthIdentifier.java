package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * What a user types into the sign-in form's first field: an email address <em>or</em> a phone
 * number (BRD FR-2.1).
 *
 * <p>Modelled as a sealed interface with an {@link Email} and a {@link Phone} case, so a {@code
 * switch} over it is exhaustive and each case carries an already-validated value type. Both cases
 * expose one {@link #normalised()} lookup key, which is the only thing a credential store should
 * query by — an email is folded to lower case and a phone loses its formatting, so {@code
 * "Ann@Example.com"} and {@code "ann@example.com"} find the same credential.
 *
 * <p><strong>Failed lookups must be indistinguishable.</strong> FR-2.2 requires a generic
 * invalid-credentials message that never reveals which field was wrong. A lookup by {@link
 * #normalised()} that finds nothing, a credential whose password hash does not match, and an input
 * that {@linkplain #parse(String) cannot be parsed} at all must therefore produce the same outcome
 * and the same message — not two exception types, not two codes. Keys of the two cases never
 * collide: an email always contains {@code @} and a phone never does.
 */
public sealed interface AuthIdentifier permits AuthIdentifier.Email, AuthIdentifier.Phone {

  /**
   * Returns the key to look this identifier up by.
   *
   * @return the normalised email address or phone number, never blank
   */
  String normalised();

  /**
   * Parses raw sign-in input: anything containing {@code @} is an email, everything else a phone.
   *
   * <p>A parse failure carries the single code {@code auth-identifier.malformed} whatever was wrong
   * with the input, and the underlying cause is kept only for logs. Callers on the sign-in path
   * must report it exactly as they report a wrong password (FR-2.2).
   *
   * @param raw what the user typed
   * @return the parsed identifier
   * @throws ValidationException if the input is null, blank, or neither a valid email nor a valid
   *     phone number
   */
  static AuthIdentifier parse(String raw) {
    if (raw == null || raw.isBlank()) {
      throw malformed("Sign-in identifier must not be null or blank");
    }
    try {
      return raw.indexOf('@') >= 0
          ? new Email(new EmailAddress(raw))
          : new Phone(new PhoneNumber(raw));
    } catch (ValidationException failure) {
      throw malformed("Malformed sign-in identifier", failure);
    }
  }

  /**
   * Builds the one failure every malformed identifier maps to (BRD FR-2.2).
   *
   * @param message the explanation, for logs
   * @return the failure to throw
   */
  private static ValidationException malformed(String message) {
    return new ValidationException("auth-identifier.malformed", message);
  }

  private static ValidationException malformed(String message, Throwable cause) {
    return new ValidationException("auth-identifier.malformed", message, cause);
  }

  /**
   * A sign-in identifier that is an email address.
   *
   * @param address the address, never null
   */
  record Email(EmailAddress address) implements AuthIdentifier {

    /**
     * Validates the address.
     *
     * @throws ValidationException if the address is null
     */
    public Email {
      if (address == null) {
        throw malformed("Sign-in email must not be null");
      }
    }

    @Override
    public String normalised() {
      return address.value();
    }
  }

  /**
   * A sign-in identifier that is a phone number.
   *
   * @param number the number, never null
   */
  record Phone(PhoneNumber number) implements AuthIdentifier {

    /**
     * Validates the number.
     *
     * @throws ValidationException if the number is null
     */
    public Phone {
      if (number == null) {
        throw malformed("Sign-in phone must not be null");
      }
    }

    @Override
    public String normalised() {
      return number.value();
    }
  }
}
