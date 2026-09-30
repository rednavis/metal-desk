package com.rednavis.metaldesk.api.account.verification;

import com.rednavis.metaldesk.api.auth.PasswordEncoderAdapter;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The verification primitive of BRD FR-2.6: <strong>issue</strong> a code by mail,
 * <strong>bind</strong> it to a subject, <strong>confirm</strong> it.
 *
 * <p>It is purpose-agnostic. It does not know what is being verified; the caller passes a {@link
 * VerificationPurpose} and does whatever should follow a successful {@link
 * VerificationOutcome.Confirmed}. Registration, password reset and, later, quick registration at
 * checkout (T-035) are all just callers.
 *
 * <p><strong>The code is a credential.</strong> It is generated from a secure random source, sent
 * only by mail, stored only as a hash made by {@link PasswordEncoderAdapter} (so off the event
 * loop), and never logged or returned. A hash of a short numeric code can be brute-forced offline
 * by anyone who reads the database, so the hash is a second line of defence; the first is that a
 * challenge is short-lived and allows only a few attempts.
 *
 * <p><strong>Challenges expire, are single-use and limit attempts</strong>, and each of those is
 * enforced by an atomic conditional update in {@link VerificationChallengeStore}. Every way for a
 * confirmation to fail returns the same {@link VerificationOutcome.Failed}; the real reason is
 * logged, without the code.
 */
@Slf4j
@Service
public class VerificationService {

  private final VerificationChallengeStore store;
  private final PasswordEncoderAdapter encoder;
  private final VerificationMailer mailer;
  private final VerificationCodes codes;
  private final VerificationProperties properties;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param store where challenges are kept
   * @param encoder hashes and checks codes
   * @param mailer sends the mail
   * @param codes generates codes and references
   * @param properties the lifetime, attempt limit and link base
   * @param clock the source of time
   */
  public VerificationService(
      VerificationChallengeStore store,
      PasswordEncoderAdapter encoder,
      VerificationMailer mailer,
      VerificationCodes codes,
      VerificationProperties properties,
      Clock clock) {
    this.store = store;
    this.encoder = encoder;
    this.mailer = mailer;
    this.codes = codes;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Issues a challenge and mails the code.
   *
   * <p>Any pending challenge of the same purpose bound to the same subject is cancelled first, so
   * only the newest code works.
   *
   * @param purpose what the challenge is for
   * @param subject who it is bound to (a customer id), or null to bind it later with {@link #bind}
   * @param email where to send the code
   * @param name how to address the recipient in the mail
   * @param locale the language of the mail
   * @return the reference to quote when confirming; the code itself is only in the mail
   */
  public Mono<VerificationTicket> issue(
      VerificationPurpose purpose, String subject, EmailAddress email, String name, Locale locale) {
    final String code = codes.code(purpose);
    final String reference = codes.reference();
    return encoder
        .encode(code)
        .flatMap(
            hash ->
                cancelEarlier(purpose, subject)
                    .then(
                        store.save(
                            new VerificationChallenge(
                                reference,
                                purpose,
                                subject,
                                email.value(),
                                hash,
                                clock.instant().plus(properties.ttlFor(purpose)),
                                0,
                                VerificationChallenge.Status.PENDING))))
        .then(Mono.defer(() -> mailer.send(purpose, email, name, locale, reference, code)))
        .thenReturn(new VerificationTicket(reference));
  }

  /**
   * Binds an issued, unbound challenge to a subject, for flows where the user is only known after
   * the code was sent.
   *
   * @param reference the challenge reference
   * @param subject the customer id to bind to
   * @return whether it was bound; false if it is unknown, not pending, or already bound
   */
  public Mono<Boolean> bind(String reference, String subject) {
    return reference == null ? Mono.just(false) : store.bind(reference, subject);
  }

  /**
   * Confirms a code.
   *
   * <p>The attempt is counted before the code is checked, so parallel guesses cannot exceed the
   * limit. A confirmation for the wrong purpose fails like any other.
   *
   * @param reference the challenge reference
   * @param code the code the user typed or followed
   * @param expected the purpose the caller is confirming
   * @return {@link VerificationOutcome.Confirmed} the first time the right code is presented in
   *     time, {@link VerificationOutcome.Failed} otherwise
   */
  public Mono<VerificationOutcome> confirm(
      String reference, String code, VerificationPurpose expected) {
    final Mono<Optional<VerificationChallenge>> counted =
        reference == null || reference.isBlank()
            ? Mono.just(Optional.<VerificationChallenge>empty())
            : store
                .countAttempt(reference, clock.instant(), properties.maxAttempts())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());
    return counted.flatMap(
        candidate -> {
          final Optional<VerificationChallenge> usable =
              candidate.filter(challenge -> challenge.purpose() == expected);
          final String hash =
              usable.map(VerificationChallenge::codeHash).orElse(encoder.dummyHash());
          return encoder
              .matches(code, hash)
              .flatMap(matched -> conclude(reference, candidate, usable, matched, expected));
        });
  }

  private Mono<VerificationOutcome> conclude(
      String reference,
      Optional<VerificationChallenge> candidate,
      Optional<VerificationChallenge> usable,
      boolean matched,
      VerificationPurpose expected) {
    final Mono<VerificationOutcome> outcome;
    if (usable.isPresent() && matched) {
      outcome =
          store
              .confirm(reference)
              .<VerificationOutcome>map(
                  confirmed ->
                      new VerificationOutcome.Confirmed(
                          confirmed.purpose(), confirmed.subject(), confirmed.email()))
              .switchIfEmpty(fail("ALREADY_USED", expected));
    } else if (candidate.isPresent()) {
      outcome = fail(usable.isPresent() ? "WRONG_CODE" : "PURPOSE_MISMATCH", expected);
    } else {
      outcome = explainUnusable(reference).flatMap(reason -> fail(reason, expected));
    }
    return outcome;
  }

  private Mono<String> explainUnusable(String reference) {
    return reference == null || reference.isBlank()
        ? Mono.just("NO_REFERENCE")
        : store
            .find(reference)
            .map(challenge -> reasonFor(challenge, clock.instant()))
            .defaultIfEmpty("NOT_FOUND");
  }

  private String reasonFor(VerificationChallenge challenge, Instant now) {
    final boolean pending = challenge.status() == VerificationChallenge.Status.PENDING;
    final boolean live = challenge.expiresAt().isAfter(now);
    return pending ? (live ? "ATTEMPTS_EXHAUSTED" : "EXPIRED") : "ALREADY_USED_OR_CANCELLED";
  }

  private static Mono<VerificationOutcome> fail(String reason, VerificationPurpose expected) {
    log.info("Verification failed for purpose {}: {}", expected, reason);
    return Mono.just(new VerificationOutcome.Failed());
  }

  private Mono<Void> cancelEarlier(VerificationPurpose purpose, String subject) {
    return subject == null ? Mono.empty() : store.invalidatePending(purpose, subject);
  }
}
