package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.rednavis.metaldesk.api.persistence.repository.CredentialRepository;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

/** Sign-in resolves every failure to one outcome, still hashes for unknown users, and throttles. */
class AuthenticationServiceTest {

  private static final String SOURCE = "203.0.113.7";
  private static final String PASSWORD = "correct-password";
  private static final String WRONG = "wrong-password";
  private static final String CUSTOMER = "c-1";
  private static final String EMAIL = "ann@example.com";
  private static final String PHONE = "+491701234567";
  private static final Duration COOL_DOWN = Duration.ofMinutes(15);

  private final BCryptPasswordEncoder real = new BCryptPasswordEncoder(4);
  private final AtomicInteger hashChecks = new AtomicInteger();
  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
  private final CustomerRepository customers = mock(CustomerRepository.class);
  private final CredentialRepository credentials = mock(CredentialRepository.class);
  private AuthenticationService service;

  @BeforeEach
  void wire() {
    final PasswordEncoder counting =
        new PasswordEncoder() {
          @Override
          public String encode(CharSequence raw) {
            return real.encode(raw);
          }

          @Override
          public boolean matches(CharSequence raw, String encoded) {
            hashChecks.incrementAndGet();
            return real.matches(raw, encoded);
          }
        };
    final JwtProperties properties = new JwtProperties("iss", "aud", Duration.ofMinutes(15), "");
    service =
        new AuthenticationService(
            customers,
            credentials,
            new PasswordEncoderAdapter(counting),
            new SignInThrottle(new ThrottleProperties(3, COOL_DOWN, List.of()), clock),
            new JwtIssuer(properties, new SecretKeySpec(new byte[32], "HmacSHA256"), clock));
    when(customers.findByEmail("nobody@example.com")).thenReturn(Mono.empty());
    when(customers.findFirstByPhone("+490000000")).thenReturn(Mono.empty());
    store(EMAIL, PHONE, VerificationState.VERIFIED, AuthCredential.State.ACTIVE);
  }

  private void store(
      String email, String phone, VerificationState state, AuthCredential.State credential) {
    final CustomerDocument customer =
        new CustomerDocument(CUSTOMER, "Ann", email, phone, List.of(), state);
    when(customers.findByEmail(email)).thenReturn(Mono.just(customer));
    when(customers.findFirstByPhone(phone)).thenReturn(Mono.just(customer));
    when(credentials.findById(CUSTOMER))
        .thenReturn(Mono.just(new CredentialDocument(CUSTOMER, real.encode(PASSWORD), credential)));
  }

  private SignInOutcome signIn(String identifier, String password) {
    return service.signIn(new SignInRequest(identifier, password), SOURCE).block();
  }

  @Test
  void signsInWithAnEmailIdentifier() {
    assertInstanceOf(SignInOutcome.Success.class, signIn(EMAIL, PASSWORD));
  }

  @Test
  void signsInWithPhoneIdentifierForTheSameCustomer() {
    assertInstanceOf(SignInOutcome.Success.class, signIn("+49 170 1234567", PASSWORD));
  }

  @Test
  void emailIsCaseInsensitive() {
    assertInstanceOf(SignInOutcome.Success.class, signIn("ANN@Example.com", PASSWORD));
  }

  @Test
  void everyKindOfFailureIsTheSameOutcome() {
    final List<SignInRequest> failures =
        List.of(
            new SignInRequest("nobody@example.com", PASSWORD),
            new SignInRequest(EMAIL, WRONG),
            new SignInRequest("+490000000", PASSWORD),
            new SignInRequest(null, PASSWORD),
            new SignInRequest(EMAIL, null),
            new SignInRequest("not an identifier", PASSWORD));

    final SignInOutcome expected = new SignInOutcome.Rejected();
    for (int i = 0; i < failures.size(); i++) {
      // A source of its own each time, so the throttle does not get in the way.
      assertEquals(expected, service.signIn(failures.get(i), "source-" + i).block());
    }
  }

  @Test
  void anUnknownIdentifierStillRunsTheHashCheck() {
    final int before = hashChecks.get();

    signIn("nobody@example.com", PASSWORD);

    assertEquals(before + 1, hashChecks.get());
  }

  @Test
  void missingAndMalformedIdentifiersAlsoRunTheHashCheck() {
    final int before = hashChecks.get();

    signIn(null, PASSWORD);
    signIn("", PASSWORD);
    signIn("not an identifier", PASSWORD);

    assertEquals(before + 3, hashChecks.get());
  }

  @Test
  void customerWithoutCredentialIsRejectedAfterHashCheck() {
    when(credentials.findById(CUSTOMER)).thenReturn(Mono.empty());
    final int before = hashChecks.get();

    assertEquals(new SignInOutcome.Rejected(), signIn(EMAIL, PASSWORD));
    assertEquals(before + 1, hashChecks.get());
  }

  @Test
  void disabledCredentialIsRejectedEvenWithTheRightPassword() {
    store(EMAIL, PHONE, VerificationState.VERIFIED, AuthCredential.State.DISABLED);

    assertEquals(new SignInOutcome.Rejected(), signIn(EMAIL, PASSWORD));
  }

  @Test
  void anUnverifiedCustomerCanSignInAndTheTokenSaysSo() {
    store(EMAIL, PHONE, VerificationState.UNVERIFIED, AuthCredential.State.ACTIVE);

    final SignInOutcome outcome = signIn(EMAIL, PASSWORD);

    assertInstanceOf(SignInOutcome.Success.class, outcome);
    assertEquals(Duration.ofMinutes(15), ((SignInOutcome.Success) outcome).token().lifetime());
  }

  @Test
  void theFourthAttemptAfterThreeFailuresIsThrottledWithoutCheckingThePassword() {
    for (int i = 0; i < 3; i++) {
      signIn(EMAIL, WRONG);
    }
    final int before = hashChecks.get();

    final SignInOutcome fourth = signIn(EMAIL, PASSWORD);

    assertInstanceOf(SignInOutcome.Throttled.class, fourth);
    assertEquals(COOL_DOWN, ((SignInOutcome.Throttled) fourth).retryAfter());
    assertEquals(before, hashChecks.get());
  }

  @Test
  void successResetsTheFailureCounter() {
    signIn(EMAIL, WRONG);
    signIn(EMAIL, WRONG);
    assertInstanceOf(SignInOutcome.Success.class, signIn(EMAIL, PASSWORD));
    signIn(EMAIL, WRONG);
    signIn(EMAIL, WRONG);

    assertInstanceOf(SignInOutcome.Success.class, signIn(EMAIL, PASSWORD));
  }

  @Test
  void theCoolDownExpires() {
    for (int i = 0; i < 3; i++) {
      signIn(EMAIL, WRONG);
    }
    clock.advance(COOL_DOWN.plusSeconds(1));

    assertInstanceOf(SignInOutcome.Success.class, signIn(EMAIL, PASSWORD));
  }

  @Test
  void differentSourceIsNotThrottled() {
    for (int i = 0; i < 3; i++) {
      signIn(EMAIL, WRONG);
    }

    assertInstanceOf(
        SignInOutcome.Success.class,
        service.signIn(new SignInRequest(EMAIL, PASSWORD), "198.51.100.1").block());
  }
}
