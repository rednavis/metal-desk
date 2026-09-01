package com.rednavis.metaldesk.payments.provider;

import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Locale;

/**
 * The validation the payment request and result types share, so each rule is written once.
 *
 * <p>It is kept out of {@link PaymentOutcome} itself: the helpers are called from that interface's
 * nested records, which static analysis does not follow for private interface methods.
 */
final class Checks {

  /** The longest allowed customer-safe message, in characters. */
  /* default */ static final int MAX_MESSAGE_LEN = 200;

  private Checks() {}

  /**
   * Returns the URI if it is somewhere a browser can be sent: absolute, with a host, and an {@code
   * http} or {@code https} scheme. That refuses relative paths and schemes such as {@code
   * javascript:} or {@code file:}, which a redirect target must never be.
   *
   * @param uri the candidate, possibly null
   * @param code the failure code to use when it is not
   * @param message the failure message to use when it is not
   * @return the same URI
   * @throws ValidationException if the URI is null, relative, hostless or not {@code http(s)}
   */
  /* default */ static URI webUri(URI uri, String code, String message) {
    final String scheme = uri == null ? null : uri.getScheme();
    final String lower = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
    if (!("http".equals(lower) || "https".equals(lower)) || uri.getHost() == null) {
      throw new ValidationException(code, message);
    }
    return uri;
  }

  /**
   * Refuses a missing provider reference.
   *
   * @param reference the candidate
   * @throws ValidationException if the reference is null
   */
  /* default */ static void reference(ProviderReference reference) {
    if (reference == null) {
      throw new ValidationException(
          "payment-outcome.reference-missing", "Provider reference must not be null");
    }
  }

  /**
   * Returns a message that is safe to show a customer: not blank, trimmed, and at most {@link
   * #MAX_MESSAGE_LEN} characters.
   *
   * @param message the candidate
   * @return the trimmed message
   * @throws ValidationException if the message is null, blank or too long
   */
  /* default */ static String safeMessage(String message) {
    if (message == null || message.isBlank()) {
      throw new ValidationException(
          "payment-outcome.message-blank", "Message must not be null or blank");
    }
    final String stripped = message.strip();
    if (stripped.length() > MAX_MESSAGE_LEN) {
      throw new ValidationException(
          "payment-outcome.message-too-long",
          "Message must be at most " + MAX_MESSAGE_LEN + " characters");
    }
    return stripped;
  }
}
