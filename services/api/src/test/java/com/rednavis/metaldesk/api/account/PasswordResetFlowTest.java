package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.VerificationChallengeRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.persistence.document.VerificationChallengeDocument;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

/** Password reset over HTTP (BRD FR-2.4): no oracle, time-limited, and old links die. */
class PasswordResetFlowTest extends AccountTestSupport {

  private static final String REQUEST_URI = "/api/account/password-reset/request";
  private static final String CONFIRM_URI = "/api/account/password-reset/confirm";
  private static final String NEW_PASSWORD = "a-brand-new-password";

  @Autowired private CustomerRepository customers;
  @Autowired private VerificationChallengeRepository challenges;

  private EntityExchangeResult<byte[]> request(String email) {
    return client
        .post()
        .uri(REQUEST_URI)
        .header(CORRELATION, "fixed-id")
        .bodyValue(Map.of("email", email))
        .exchange()
        .expectBody()
        .returnResult();
  }

  private void confirm(String reference, String code, String password, int status) {
    client
        .post()
        .uri(CONFIRM_URI)
        .bodyValue(Map.of("reference", reference, "code", code, "newPassword", password))
        .exchange()
        .expectStatus()
        .isEqualTo(status);
  }

  @Test
  void unknownAddressGetsSameResponseAsKnownOneAndNoMail() {
    final String known = freshEmail();
    registerAndVerify(known);
    final String unknown = freshEmail();

    final EntityExchangeResult<byte[]> forKnown = request(known);
    final EntityExchangeResult<byte[]> forUnknown = request(unknown);

    assertEquals(202, forKnown.getStatus().value());
    assertEquals(forKnown.getStatus(), forUnknown.getStatus());
    assertArrayEquals(forKnown.getResponseBody(), forUnknown.getResponseBody());
    assertEquals(0, mailTo(unknown).size());
    assertEquals(MailTemplate.PASSWORD_RESET, mailTo(known).get(1).template());
  }

  @Test
  void anUnverifiedAddressGetsTheSameResponseAndNoResetMail() {
    final String known = freshEmail();
    registerAndVerify(known);
    final String unverified = freshEmail();
    register(unverified);

    final EntityExchangeResult<byte[]> forKnown = request(known);
    final EntityExchangeResult<byte[]> forUnverified = request(unverified);

    assertArrayEquals(forKnown.getResponseBody(), forUnverified.getResponseBody());
    assertEquals(1, mailTo(unverified).size());
  }

  @Test
  void theLinkResetsThePasswordAndTheOldOneStopsWorking() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    final TransactionalMail link = mailTo(email).get(1);

    confirm(MailInspector.linkReference(link), MailInspector.linkCode(link), NEW_PASSWORD, 200);

    assertNull(signIn(email, PASSWORD));
    assertNotNull(signIn(email, NEW_PASSWORD));
  }

  @Test
  void linkWorksOnlyOnce() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    final TransactionalMail link = mailTo(email).get(1);
    final String reference = MailInspector.linkReference(link);
    final String code = MailInspector.linkCode(link);

    confirm(reference, code, NEW_PASSWORD, 200);

    confirm(reference, code, "yet-another-password", 400);
  }

  @Test
  void expiredLinkFailsLikeWrongCode() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    final TransactionalMail link = mailTo(email).get(1);
    final String reference = MailInspector.linkReference(link);
    expire(reference);

    confirm(reference, MailInspector.linkCode(link), NEW_PASSWORD, 400);
    confirm(reference, "wrong", NEW_PASSWORD, 400);
    assertNotNull(signIn(email, PASSWORD));
  }

  @Test
  void weakNewPasswordIsRefusedWithoutSpendingTheLink() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    final TransactionalMail link = mailTo(email).get(1);
    final String reference = MailInspector.linkReference(link);
    final String code = MailInspector.linkCode(link);

    confirm(reference, code, "short", 400);

    confirm(reference, code, NEW_PASSWORD, 200);
  }

  @Test
  void completingResetCancelsOtherOutstandingResetChallenges() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    final TransactionalMail link = mailTo(email).get(1);
    final String subject = Objects.requireNonNull(customers.findByEmail(email).block()).id();
    challenges
        .save(
            new VerificationChallengeDocument(
                "other-outstanding-" + subject,
                "PASSWORD_RESET",
                subject,
                email,
                "$2a$04$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ01234",
                Instant.now().plusSeconds(600),
                0,
                "PENDING"))
        .block();

    confirm(MailInspector.linkReference(link), MailInspector.linkCode(link), NEW_PASSWORD, 200);

    assertEquals(
        "INVALIDATED",
        Objects.requireNonNull(challenges.findById("other-outstanding-" + subject).block())
            .status());
  }

  @Test
  void newRequestKillsTheEarlierLink() {
    final String email = freshEmail();
    registerAndVerify(email);
    request(email);
    request(email);
    final TransactionalMail first = mailTo(email).get(1);
    final TransactionalMail second = mailTo(email).get(2);

    confirm(MailInspector.linkReference(first), MailInspector.linkCode(first), NEW_PASSWORD, 400);
    confirm(MailInspector.linkReference(second), MailInspector.linkCode(second), NEW_PASSWORD, 200);
  }

  @Test
  void badAddressIsValidationErrorNotOracle() {
    client
        .post()
        .uri(REQUEST_URI)
        .bodyValue(Map.of("email", "not-an-email"))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  private void expire(String reference) {
    challenges
        .findById(reference)
        .flatMap(
            found ->
                challenges.save(
                    new VerificationChallengeDocument(
                        found.reference(),
                        found.purpose(),
                        found.subject(),
                        found.email(),
                        found.codeHash(),
                        Instant.now().minusSeconds(60),
                        found.attempts(),
                        found.status())))
        .block();
  }
}
