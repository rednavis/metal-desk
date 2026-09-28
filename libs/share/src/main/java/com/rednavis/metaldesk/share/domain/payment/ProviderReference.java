package com.rednavis.metaldesk.share.domain.payment;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.regex.Pattern;

/**
 * The opaque handle a payment provider gave for a payment: a token, a charge id or an invoice
 * number.
 *
 * <p>It is a single string the platform never interprets, and it is deliberately narrow: printable
 * ASCII without whitespace, at most {@value #MAX_LENGTH} characters. That is enough for any real
 * provider handle and too little room for a free-form note, a formatted number with spaces or a
 * pasted response. Together with {@link PaymentRecord} holding nothing else, it keeps the
 * platform's promise that it never stores raw payment instrument data.
 *
 * @param value the reference, never blank
 */
public record ProviderReference(String value) {

  /** The longest allowed reference, in characters. */
  public static final int MAX_LENGTH = 128;

  private static final Pattern FORMAT = Pattern.compile("[\\x21-\\x7E]{1," + MAX_LENGTH + "}");

  /**
   * Validates the value.
   *
   * @throws ValidationException if the value is null, blank, longer than {@value #MAX_LENGTH}
   *     characters, or holds whitespace or anything but printable ASCII
   */
  public ProviderReference {
    if (value == null || value.isBlank()) {
      throw new ValidationException(
          "provider-reference.blank", "Provider reference must not be null or blank");
    }
    value = value.strip();
    if (!FORMAT.matcher(value).matches()) {
      throw new ValidationException(
          "provider-reference.malformed",
          "Provider reference must be 1 to "
              + MAX_LENGTH
              + " printable ASCII characters without spaces");
    }
  }
}
