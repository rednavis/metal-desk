package com.rednavis.metaldesk.mail;

import reactor.core.publisher.Mono;

/**
 * The single interface through which the platform sends email (ADR-0002). Callers send a {@link
 * TransactionalMail} and never a provider's client, so which provider sends it, or that nothing
 * does, is a deployment choice.
 *
 * <p>It is reactive: sending is I/O, and the whole stack is non-blocking (Architecture section 5),
 * so it returns a {@link Mono} and an implementation must never block the caller's thread. The
 * {@code Mono} does nothing until subscribed, and completes empty once the mail has been accepted.
 * A transport that fails ends it with a {@link MailException}.
 *
 * <p>In this reference build the implementation is {@link
 * com.rednavis.metaldesk.mail.fake.InProcessMailSender}, an in-process fake, because a mail
 * provider has no HTTP boundary to intercept: the substitution point is this interface. A real
 * sender arrives with deployment.
 */
@FunctionalInterface
public interface MailSender {

  /**
   * Sends a mail.
   *
   * @param mail the rendered mail, with its recipients
   * @return a {@code Mono} that completes once the mail has been accepted, or errors with a {@link
   *     MailException} if it could not be sent
   */
  Mono<Void> send(TransactionalMail mail);
}
