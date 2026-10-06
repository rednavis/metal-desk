package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A validated, normalised email address (BRD FR-2.1, FR-4.1).
 *
 * <p>The value is trimmed and lower-cased at construction, so {@code "Ann@Example.COM"} equals
 * {@code "ann@example.com"}. Strictly the local part is case-sensitive under RFC 5321, but no
 * mainstream mailbox provider treats it so, and sign-in (FR-2.1) must not fail because a customer
 * typed a capital letter. The format check is deliberately shallow — one {@code @}, a dotted
 * domain, no whitespace, at most 254 characters: whether the mailbox exists is what email
 * verification (FR-2.3) is for.
 *
 * <p>Failure messages never echo the address, because it is personal data that ends up in logs.
 *
 * @param value the normalised address
 */
public record EmailAddress(String value) {

  private static final int MAX_LENGTH = 254;
  private static final Pattern FORMAT = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s.]+");

  /**
   * Validates and normalises the address.
   *
   * @throws ValidationException if the address is null, blank or malformed
   */
  public EmailAddress {
    if (value == null || value.isBlank()) {
      throw new ValidationException("email.blank", "Email address must not be null or blank");
    }
    value = value.strip().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
      throw new ValidationException("email.malformed", "Malformed email address");
    }
  }
}
