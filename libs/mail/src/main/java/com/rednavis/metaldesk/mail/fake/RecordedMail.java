package com.rednavis.metaldesk.mail.fake;

import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * One send recorded by the {@link InProcessMailSender}: the mail, and its position in the order the
 * sends were accepted.
 *
 * <p>The sequence number stands in for a timestamp, which would need a clock: it is unique and
 * increases with each send, so tests can assert what was sent and in which order.
 *
 * @param sequence the position of this send, starting at 1 and never reused
 * @param mail the mail that was sent, never null
 */
public record RecordedMail(long sequence, TransactionalMail mail) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the sequence is below 1 or the mail is null
   */
  public RecordedMail {
    if (sequence < 1 || mail == null) {
      throw new ValidationException(
          "recorded-mail.invalid", "A recorded mail needs a positive sequence and a mail");
    }
  }
}
