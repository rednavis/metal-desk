package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.account.verification.VerificationChallengeStore;
import com.rednavis.metaldesk.api.account.verification.VerificationCodes;
import com.rednavis.metaldesk.api.account.verification.VerificationMailer;
import com.rednavis.metaldesk.api.account.verification.VerificationOutcome;
import com.rednavis.metaldesk.api.account.verification.VerificationProperties;
import com.rednavis.metaldesk.api.account.verification.VerificationPurpose;
import com.rednavis.metaldesk.api.account.verification.VerificationService;
import com.rednavis.metaldesk.api.account.verification.VerificationTicket;
import com.rednavis.metaldesk.api.auth.MutableClock;
import com.rednavis.metaldesk.api.auth.PasswordEncoderAdapter;
import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.api.persistence.repository.VerificationChallengeRepository;
import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.mail.fake.InProcessMailSender;
import com.rednavis.metaldesk.persistence.document.VerificationChallengeDocument;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

/**
 * The FR-2.6 primitive on its own: purpose-agnostic, hashed, expiring, single-use, attempt-limited.
 */
class VerificationServiceTest extends MongoTestSupport {

  private static final int MAX_ATTEMPTS = 5;
  private static final Duration TTL = Duration.ofMinutes(15);
  private static final String WRONG = "000000";
  private static final String NAME = "Ann";

  @Autowired private VerificationChallengeStore store;
  @Autowired private PasswordEncoderAdapter encoder;
  @Autowired private VerificationCodes codes;
  @Autowired private VerificationChallengeRepository challenges;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
  private final InProcessMailSender mail = new InProcessMailSender();
  private VerificationService service;
  private EmailAddress email;
  private String subject;

  @BeforeEach
  void wire() {
    final VerificationProperties properties =
        new VerificationProperties(
            TTL, Duration.ofMinutes(30), MAX_ATTEMPTS, "https://example.test/reset");
    service =
        new VerificationService(
            store,
            encoder,
            new VerificationMailer(new MailRenderer(), mail, properties),
            codes,
            properties,
            clock);
    subject = "customer-" + UUID.randomUUID();
    email = new EmailAddress("verify-" + UUID.randomUUID() + "@example.com");
  }

  private VerificationTicket issue(VerificationPurpose purpose) {
    return service.issue(purpose, subject, email, NAME, Locale.ENGLISH).block();
  }

  private TransactionalMail latestMail() {
    final List<TransactionalMail> sent = MailInspector.to(mail, email.value());
    return sent.get(sent.size() - 1);
  }

  private String codeFor(VerificationPurpose purpose) {
    return purpose == VerificationPurpose.PASSWORD_RESET
        ? MailInspector.linkCode(latestMail())
        : MailInspector.typedCode(latestMail());
  }

  private VerificationOutcome confirmed(VerificationPurpose purpose) {
    return new VerificationOutcome.Confirmed(purpose, subject, email.value());
  }

  private VerificationOutcome confirm(String reference, String code, VerificationPurpose purpose) {
    return service.confirm(reference, code, purpose).block();
  }

  @Test
  void worksForEveryPurposeWithTheRightMail() {
    for (final VerificationPurpose purpose : VerificationPurpose.values()) {
      final VerificationTicket ticket = issue(purpose);

      assertEquals(purpose.template(), latestMail().template());
      assertEquals(confirmed(purpose), confirm(ticket.reference(), codeFor(purpose), purpose));
    }
  }

  @Test
  void resetAndRegistrationCanBeOutstandingTogether() {
    final VerificationTicket registration = issue(VerificationPurpose.REGISTRATION);
    final String registrationCode = codeFor(VerificationPurpose.REGISTRATION);
    final VerificationTicket reset = issue(VerificationPurpose.PASSWORD_RESET);
    final String resetCode = codeFor(VerificationPurpose.PASSWORD_RESET);

    assertInstanceOfConfirmed(
        confirm(reset.reference(), resetCode, VerificationPurpose.PASSWORD_RESET));
    assertInstanceOfConfirmed(
        confirm(registration.reference(), registrationCode, VerificationPurpose.REGISTRATION));
  }

  private static void assertInstanceOfConfirmed(VerificationOutcome outcome) {
    assertTrue(outcome instanceof VerificationOutcome.Confirmed, outcome.toString());
  }

  @Test
  void codeConfirmsOnlyOnce() {
    final VerificationTicket ticket = issue(VerificationPurpose.REGISTRATION);
    final String code = codeFor(VerificationPurpose.REGISTRATION);

    assertInstanceOfConfirmed(confirm(ticket.reference(), code, VerificationPurpose.REGISTRATION));
    assertEquals(
        new VerificationOutcome.Failed(),
        confirm(ticket.reference(), code, VerificationPurpose.REGISTRATION));
  }

  @Test
  void everyKindOfFailureIsTheSameOutcome() {
    final VerificationPurpose purpose = VerificationPurpose.REGISTRATION;
    final VerificationOutcome failed = new VerificationOutcome.Failed();

    final VerificationTicket wrong = issue(purpose);
    assertEquals(failed, confirm(wrong.reference(), WRONG, purpose));

    final VerificationTicket expired =
        service.issue(purpose, subject + "-e", email, NAME, Locale.ENGLISH).block();
    final String expiredCode = codeFor(purpose);
    clock.advance(TTL.plusSeconds(1));
    assertEquals(failed, confirm(expired.reference(), expiredCode, purpose));

    final VerificationTicket exhausted =
        service.issue(purpose, subject + "-x", email, NAME, Locale.ENGLISH).block();
    final String exhaustedCode = codeFor(purpose);
    for (int i = 0; i < MAX_ATTEMPTS; i++) {
      confirm(exhausted.reference(), WRONG, purpose);
    }
    assertEquals(failed, confirm(exhausted.reference(), exhaustedCode, purpose));

    assertEquals(failed, confirm("no-such-reference", WRONG, purpose));
    assertEquals(failed, confirm(null, null, purpose));
  }

  @Test
  void codeForOnePurposeDoesNotConfirmAnother() {
    final VerificationTicket ticket = issue(VerificationPurpose.PASSWORD_RESET);
    final String code = codeFor(VerificationPurpose.PASSWORD_RESET);

    assertEquals(
        new VerificationOutcome.Failed(),
        confirm(ticket.reference(), code, VerificationPurpose.REGISTRATION));
  }

  @Test
  void theStoredChallengeHoldsNoPlaintextCode() {
    final VerificationTicket ticket = issue(VerificationPurpose.REGISTRATION);
    final String code = codeFor(VerificationPurpose.REGISTRATION);

    final VerificationChallengeDocument stored = challenges.findById(ticket.reference()).block();

    assertFalse(stored.codeHash().contains(code));
    assertTrue(stored.codeHash().startsWith("$2"), "expected a BCrypt hash");
    assertFalse(stored.toString().contains(stored.codeHash()));
  }

  @Test
  void newChallengeCancelsAnEarlierOneOfTheSamePurposeAndSubject() {
    final VerificationTicket first = issue(VerificationPurpose.PASSWORD_RESET);
    final String firstCode = codeFor(VerificationPurpose.PASSWORD_RESET);
    final VerificationTicket second = issue(VerificationPurpose.PASSWORD_RESET);
    final String secondCode = codeFor(VerificationPurpose.PASSWORD_RESET);

    assertEquals(
        new VerificationOutcome.Failed(),
        confirm(first.reference(), firstCode, VerificationPurpose.PASSWORD_RESET));
    assertInstanceOfConfirmed(
        confirm(second.reference(), secondCode, VerificationPurpose.PASSWORD_RESET));
  }

  @Test
  void challengeIssuedWithoutSubjectCanBeBoundOnce() {
    final VerificationTicket ticket =
        service
            .issue(
                VerificationPurpose.CHECKOUT_QUICK_REGISTRATION, null, email, NAME, Locale.ENGLISH)
            .block();
    final String code = codeFor(VerificationPurpose.CHECKOUT_QUICK_REGISTRATION);

    assertTrue(service.bind(ticket.reference(), subject).block());
    assertFalse(service.bind(ticket.reference(), "someone-else").block());
    assertEquals(
        new VerificationOutcome.Confirmed(
            VerificationPurpose.CHECKOUT_QUICK_REGISTRATION, subject, email.value()),
        confirm(ticket.reference(), code, VerificationPurpose.CHECKOUT_QUICK_REGISTRATION));
  }

  @Test
  void simultaneousConfirmationsOfTheRightCodeSucceedExactlyOnce() {
    final VerificationTicket ticket = issue(VerificationPurpose.REGISTRATION);
    final String code = codeFor(VerificationPurpose.REGISTRATION);

    final List<VerificationOutcome> outcomes =
        Flux.range(0, 6)
            .flatMap(
                i -> service.confirm(ticket.reference(), code, VerificationPurpose.REGISTRATION))
            .collectList()
            .block();

    assertEquals(
        1, outcomes.stream().filter(VerificationOutcome.Confirmed.class::isInstance).count());
  }
}
