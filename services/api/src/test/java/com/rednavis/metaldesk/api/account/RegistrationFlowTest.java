package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.VerificationChallengeRepository;
import com.rednavis.metaldesk.mail.MailTemplate;
import com.rednavis.metaldesk.mail.TransactionalMail;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

/** Registration and email confirmation over HTTP (BRD FR-2.3, FR-2.6). */
class RegistrationFlowTest extends AccountTestSupport {

  private static final String VERIFY_URI = "/api/account/verify-email";
  private static final String REFERENCE = "reference";
  private static final String CODE = "code";

  @Autowired private CustomerRepository customers;
  @Autowired private VerificationChallengeRepository challenges;

  private CustomerDocument customer(String email) {
    return Objects.requireNonNull(customers.findByEmail(email).block());
  }

  private EntityExchangeResult<byte[]> verify(Object body) {
    return client
        .post()
        .uri(VERIFY_URI)
        .header(CORRELATION, "fixed-id")
        .bodyValue(body)
        .exchange()
        .expectBody()
        .returnResult();
  }

  @Test
  void registrationCreatesAnUnverifiedCustomerAndSendsOneVerificationMail() {
    final String email = freshEmail();

    final Registered result = register(email);

    assertEquals(202, result.status());
    assertNotNull(result.body().get(REFERENCE));
    assertEquals(VerificationState.UNVERIFIED, customer(email).verification());
    final List<TransactionalMail> sent = mailTo(email);
    assertEquals(1, sent.size());
    assertEquals(MailTemplate.EMAIL_VERIFICATION, sent.get(0).template());
  }

  @Test
  void registrationDoesNotSignTheCustomerIn() {
    final String email = freshEmail();

    final Map<String, Object> body = register(email).body();

    assertNull(body.get("accessToken"));
    assertEquals(2, body.size());
  }

  @Test
  void theCorrectCodeVerifiesTheCustomerAndSecondUseFails() {
    final String email = freshEmail();
    final Object reference = register(email).body().get(REFERENCE);
    final String code = MailInspector.typedCode(mailTo(email).get(0));

    client
        .post()
        .uri(VERIFY_URI)
        .bodyValue(Map.of(REFERENCE, reference, CODE, code))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.verified")
        .isEqualTo(true);
    assertEquals(VerificationState.VERIFIED, customer(email).verification());

    client
        .post()
        .uri(VERIFY_URI)
        .bodyValue(Map.of(REFERENCE, reference, CODE, code))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @Test
  void theTokenIsUnverifiedUntilTheEmailIsConfirmedAndVerifiedAfter() {
    final String email = freshEmail();
    final Object reference = register(email).body().get(REFERENCE);

    final String before = signIn(email, PASSWORD);
    client
        .get()
        .uri("/api/auth/me")
        .headers(headers -> headers.setBearerAuth(before))
        .exchange()
        .expectBody()
        .jsonPath("$.verification")
        .isEqualTo("UNVERIFIED");

    client
        .post()
        .uri(VERIFY_URI)
        .bodyValue(
            Map.of(REFERENCE, reference, CODE, MailInspector.typedCode(mailTo(email).get(0))))
        .exchange()
        .expectStatus()
        .isOk();
    final String after = signIn(email, PASSWORD);
    client
        .get()
        .uri("/api/auth/me")
        .headers(headers -> headers.setBearerAuth(after))
        .exchange()
        .expectBody()
        .jsonPath("$.verification")
        .isEqualTo("VERIFIED");
  }

  @Test
  void wrongExpiredAndExhaustedConfirmationsAreByteIdentical() {
    final String wrongEmail = freshEmail();
    final Object wrongRef = register(wrongEmail).body().get(REFERENCE);
    final String expiredEmail = freshEmail();
    final Object expiredRef = register(expiredEmail).body().get(REFERENCE);
    final String exhaustedEmail = freshEmail();
    final Object exhaustedRef = register(exhaustedEmail).body().get(REFERENCE);

    final EntityExchangeResult<byte[]> wrong = verify(Map.of(REFERENCE, wrongRef, CODE, "000000"));

    challenges
        .findById((String) expiredRef)
        .flatMap(
            found ->
                challenges.save(
                    new com.rednavis.metaldesk.api.persistence.document
                        .VerificationChallengeDocument(
                        found.reference(),
                        found.purpose(),
                        found.subject(),
                        found.email(),
                        found.codeHash(),
                        Instant.now().minusSeconds(60),
                        found.attempts(),
                        found.status())))
        .block();
    final EntityExchangeResult<byte[]> expired =
        verify(
            Map.of(
                REFERENCE, expiredRef, CODE, MailInspector.typedCode(mailTo(expiredEmail).get(0))));

    for (int i = 0; i < 5; i++) {
      verify(Map.of(REFERENCE, exhaustedRef, CODE, "000000"));
    }
    final EntityExchangeResult<byte[]> exhausted =
        verify(
            Map.of(
                REFERENCE,
                exhaustedRef,
                CODE,
                MailInspector.typedCode(mailTo(exhaustedEmail).get(0))));
    final EntityExchangeResult<byte[]> unknown = verify(Map.of(REFERENCE, "nope", CODE, "000000"));

    assertEquals(400, wrong.getStatus().value());
    for (final EntityExchangeResult<byte[]> other :
        new EntityExchangeResult[] {expired, exhausted, unknown}) {
      assertEquals(wrong.getStatus(), other.getStatus());
      assertArrayEquals(wrong.getResponseBody(), other.getResponseBody());
    }
  }

  @Test
  void registeringKnownAddressLooksTheSameAndChangesNothing() {
    final String email = freshEmail();
    registerAndVerify(email);
    final int mailBefore = mailTo(email).size();

    final Registered again = register(email);

    assertEquals(202, again.status());
    assertEquals(2, again.body().size());
    assertNotNull(again.body().get(REFERENCE));
    assertEquals(mailBefore, mailTo(email).size());
    assertEquals(VerificationState.VERIFIED, customer(email).verification());
  }

  @Test
  void registeringUnverifiedAddressAgainSendsFreshCode() {
    final String email = freshEmail();
    register(email);

    final Object reference = register(email).body().get(REFERENCE);

    assertEquals(2, mailTo(email).size());
    client
        .post()
        .uri(VERIFY_URI)
        .bodyValue(
            Map.of(REFERENCE, reference, CODE, MailInspector.typedCode(mailTo(email).get(1))))
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void invalidProfilesAreRejected() {
    final String register = "/api/account/register";
    client
        .post()
        .uri(register)
        .bodyValue(Map.of("name", "Ann", "email", "not-an-email", "password", PASSWORD))
        .exchange()
        .expectStatus()
        .isBadRequest();
    client
        .post()
        .uri(register)
        .bodyValue(Map.of("name", "Ann", "email", freshEmail(), "password", "short"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("password.invalid");
    client
        .post()
        .uri(register)
        .bodyValue(Map.of("name", " ", "email", freshEmail(), "password", PASSWORD))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }
}
