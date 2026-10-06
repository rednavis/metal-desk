package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.regex.Pattern;

/**
 * A validated phone number, stored with its formatting stripped (BRD FR-2.1, FR-4.1).
 *
 * <p>The customer may type {@code "+49 (170) 123-4567"}; the value kept is {@code "+491701234567"}:
 * digits only, with a leading {@code +} preserved when one was typed. Spaces, dots, dashes and
 * parentheses are accepted as separators; letters and any other character are refused. The number
 * must hold 7 to 15 digits (the E.164 maximum).
 *
 * <p>No country is assumed, so a number typed without {@code +} is kept as typed and will not equal
 * its international form. Resolving that needs a default region, which is a checkout concern, not a
 * domain one.
 *
 * <p>Failure messages never echo the number, because it is personal data that ends up in logs.
 *
 * @param value the normalised number
 */
public record PhoneNumber(String value) {

  private static final int MIN_DIGITS = 7;
  private static final int MAX_DIGITS = 15;
  private static final Pattern ALLOWED = Pattern.compile("\\+?[0-9 ().\\-]+");
  private static final Pattern NON_DIGITS = Pattern.compile("\\D");

  /**
   * Validates and normalises the number.
   *
   * @throws ValidationException if the number is null, blank or malformed
   */
  public PhoneNumber {
    if (value == null || value.isBlank()) {
      throw new ValidationException("phone.blank", "Phone number must not be null or blank");
    }
    final String stripped = value.strip();
    if (!ALLOWED.matcher(stripped).matches()) {
      throw new ValidationException("phone.malformed", "Malformed phone number");
    }
    final String digits = NON_DIGITS.matcher(stripped).replaceAll("");
    if (digits.length() < MIN_DIGITS || digits.length() > MAX_DIGITS) {
      throw new ValidationException("phone.malformed", "Malformed phone number");
    }
    value = stripped.charAt(0) == '+' ? '+' + digits : digits;
  }
}
