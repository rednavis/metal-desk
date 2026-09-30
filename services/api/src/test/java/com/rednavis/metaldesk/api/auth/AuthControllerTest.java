package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import com.rednavis.metaldesk.api.persistence.repository.CredentialRepository;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.persistence.document.CredentialDocument;
import com.rednavis.metaldesk.persistence.fixtures.AccountFixtures;
import com.rednavis.metaldesk.persistence.mapper.CustomerMapper;
import com.rednavis.metaldesk.share.domain.customer.AuthCredential;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Sign-in over HTTP against real MongoDB: response identity, tokens, public routes, throttling. */
@AutoConfigureWebTestClient
class AuthControllerTest extends MongoTestSupport {

  private static final String SIGN_IN = "/api/auth/sign-in";
  private static final String PASSWORD = "correct-password";
  private static final String EMAIL = "auth-ann@example.com";
  private static final String PHONE = "+491701234599";
  private static final String CORRELATION = "X-Correlation-Id";
  private static final String PASSWORD_FIELD = "password";
  private static final String WRONG = "wrong-password";

  @Autowired private WebTestClient client;
  @Autowired private CustomerRepository customers;
  @Autowired private CredentialRepository credentials;
  @Autowired private CustomerMapper customerMapper;
  @Autowired private PasswordEncoder encoder;
  @Autowired private SignInThrottle throttle;

  @BeforeEach
  void seed() {
    customers
        .save(customerMapper.toDocument(AccountFixtures.customer("auth-1", EMAIL, PHONE)))
        .block();
    credentials
        .save(
            new CredentialDocument("auth-1", encoder.encode(PASSWORD), AuthCredential.State.ACTIVE))
        .block();
    throttle.recordSuccess(ClientAddressResolver.UNKNOWN);
  }

  private EntityExchangeResult<byte[]> signIn(Object body) {
    // Each attempt starts with a clean throttle, so the requests compared below are all failures
    // of the same kind rather than some of them being throttled.
    throttle.recordSuccess(ClientAddressResolver.UNKNOWN);
    final WebTestClient.RequestBodySpec spec =
        client
            .post()
            .uri(SIGN_IN)
            .header(CORRELATION, "fixed-id")
            .header("Content-Type", "application/json");
    return (body == null ? spec.exchange() : spec.bodyValue(body).exchange())
        .expectBody()
        .returnResult();
  }

  private static Map<String, String> body(String identifier, String password) {
    return Map.of("identifier", identifier, PASSWORD_FIELD, password);
  }

  private String token() {
    final Map<?, ?> response =
        client
            .post()
            .uri(SIGN_IN)
            .bodyValue(body(EMAIL, PASSWORD))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();
    return (String) Objects.requireNonNull(response).get("accessToken");
  }

  @Test
  void signsInWithEmailAndWithPhone() {
    for (final String identifier : new String[] {EMAIL, PHONE}) {
      throttle.recordSuccess(ClientAddressResolver.UNKNOWN);
      client
          .post()
          .uri(SIGN_IN)
          .bodyValue(body(identifier, PASSWORD))
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .jsonPath("$.tokenType")
          .isEqualTo("Bearer")
          .jsonPath("$.expiresInSeconds")
          .isEqualTo(900)
          .jsonPath("$.accessToken")
          .isNotEmpty();
    }
  }

  @Test
  void unknownIdentifierWrongPasswordAndMissingIdentifierAreByteIdentical() {
    final EntityExchangeResult<byte[]> unknown = signIn(body("nobody@example.com", PASSWORD));
    final EntityExchangeResult<byte[]> wrong = signIn(body(EMAIL, WRONG));
    final EntityExchangeResult<byte[]> missing = signIn(Map.of(PASSWORD_FIELD, PASSWORD));
    final EntityExchangeResult<byte[]> noBody = signIn(null);

    assertEquals(HttpStatus.UNAUTHORIZED, unknown.getStatus());
    for (final EntityExchangeResult<byte[]> other : List.of(wrong, missing, noBody)) {
      assertEquals(unknown.getStatus(), other.getStatus());
      assertArrayEquals(unknown.getResponseBody(), other.getResponseBody());
    }
    assertEquals(
        "{\"code\":\"auth.invalid-credentials\",\"message\":\"Invalid credentials\","
            + "\"correlationId\":\"fixed-id\"}",
        new String(unknown.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8));
  }

  @Test
  void tokenOpensProtectedRoute() {
    client
        .get()
        .uri("/api/auth/me")
        .headers(headers -> headers.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.customerId")
        .isEqualTo("auth-1")
        .jsonPath("$.verification")
        .isEqualTo("VERIFIED");
  }

  @Test
  void signOutNeedsTokenAndConfirms() {
    client.post().uri("/api/auth/sign-out").exchange().expectStatus().isUnauthorized();
    client
        .post()
        .uri("/api/auth/sign-out")
        .headers(headers -> headers.setBearerAuth(token()))
        .exchange()
        .expectStatus()
        .isNoContent();
  }

  @Test
  void catalogMarketDataAndHealthNeedNoToken() {
    client.get().uri("/api/catalog/categories").exchange().expectStatus().isOk();
    client.get().uri("/api/market-data/prices").exchange().expectStatus().isOk();
    client.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  void everyOtherRouteIs401WithTheEnvelope() {
    client
        .get()
        .uri("/api/auth/me")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals("WWW-Authenticate", "Bearer")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("auth.unauthorized")
        .jsonPath("$.correlationId")
        .isNotEmpty();
    client.get().uri("/api/orders").exchange().expectStatus().isUnauthorized();
    client.get().uri("/anything/else").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void badTokenIs401() {
    client
        .get()
        .uri("/api/auth/me")
        .headers(headers -> headers.setBearerAuth("not.a.token"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void thePostToSignInIsPublicButOtherMethodsOnItAreNot() {
    client.get().uri(SIGN_IN).exchange().expectStatus().isUnauthorized();
  }

  @Test
  void theFourthAttemptAfterThreeFailuresIs429WithRetryAfter() {
    for (int i = 0; i < 3; i++) {
      client
          .post()
          .uri(SIGN_IN)
          .bodyValue(body(EMAIL, WRONG))
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }

    client
        .post()
        .uri(SIGN_IN)
        .bodyValue(body(EMAIL, PASSWORD))
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("auth.throttled");
  }
}
