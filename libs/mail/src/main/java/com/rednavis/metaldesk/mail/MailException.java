package com.rednavis.metaldesk.mail;

import java.io.Serial;

/**
 * Signals that a mail could not be sent because the transport failed, such as a provider that could
 * not be reached or refused the mail. It travels as the error signal of the {@code Mono} a {@link
 * MailSender} returns.
 *
 * <p>It is not a {@code DomainException}. That hierarchy is closed to three kinds, each mapped to
 * an HTTP class for a failure a caller can fix or a state that forbids a request, and a failing
 * mail transport is neither: it is infrastructure, like a payment provider that cannot be reached.
 * Callers decide what it means for them; in particular a failed notification should not fail an
 * order.
 *
 * <p>The message must not include the recipient's address or the mail's content, because both are
 * personal data that ends up in logs.
 */
public final class MailException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param message what went wrong, without any recipient address or mail content
   * @param cause the underlying transport error, or {@code null} when there is none
   */
  public MailException(String message, Throwable cause) {
    super(message, cause);
  }
}
