package com.rednavis.metaldesk.mail.fake;

import com.rednavis.metaldesk.mail.MailSender;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import reactor.core.publisher.Mono;

/**
 * A {@link MailSender} that sends nothing: it records every mail in memory and lets a test ask what
 * was sent. <strong>It is for tests and local development only</strong> and must never be wired
 * into a deployment, where it would silently swallow every customer's email.
 *
 * <p>It is a queryable recorder, not a no-op. That is what lets the whole system run locally with
 * no mail account, and what lets an end-to-end test assert that a confirmation was actually
 * produced rather than hoping it was (ADR-0002).
 *
 * <p>It is thread-safe: sends may arrive from many threads of a reactive pipeline, and every one
 * appears in {@link #sent()} exactly once, in the order of its sequence number. A send is recorded
 * when the returned {@code Mono} is subscribed, like a real send, not when {@link #send} is called.
 */
public final class InProcessMailSender implements MailSender {

  private final ReentrantLock lock = new ReentrantLock();
  private final List<RecordedMail> recorded = new ArrayList<>();
  private long lastSequence;

  /** Creates an empty sender. */
  public InProcessMailSender() {
    // Nothing to configure.
  }

  /**
   * Records a mail when the returned {@code Mono} is subscribed.
   *
   * @param mail the mail
   * @return a {@code Mono} that completes once the mail is recorded; it errors with a {@code
   *     ValidationException} if the mail is null
   */
  @Override
  public Mono<Void> send(TransactionalMail mail) {
    return mail == null
        ? Mono.error(new ValidationException("mail-sender.mail-missing", "A mail is required"))
        : Mono.fromRunnable(() -> record(mail));
  }

  /**
   * Returns everything sent so far.
   *
   * @return a snapshot of the recorded mail, in the order it was recorded
   */
  public List<RecordedMail> sent() {
    lock.lock();
    try {
      return List.copyOf(recorded);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the mail addressed to someone, including as one of several recipients.
   *
   * @param recipient the address to look for
   * @return the matching recorded mail, in order
   */
  public List<RecordedMail> sentTo(EmailAddress recipient) {
    return sent().stream().filter(entry -> entry.mail().to().contains(recipient)).toList();
  }

  /**
   * Returns the mail that was made from a template.
   *
   * @param template the template to look for
   * @return the matching recorded mail, in order
   */
  public List<RecordedMail> sentOf(MailTemplate template) {
    return sent().stream().filter(entry -> entry.mail().template() == template).toList();
  }

  /** Forgets everything recorded. Sequence numbers keep counting, so none is ever reused. */
  public void clear() {
    lock.lock();
    try {
      recorded.clear();
    } finally {
      lock.unlock();
    }
  }

  private void record(TransactionalMail mail) {
    lock.lock();
    try {
      lastSequence++;
      recorded.add(new RecordedMail(lastSequence, mail));
    } finally {
      lock.unlock();
    }
  }
}
