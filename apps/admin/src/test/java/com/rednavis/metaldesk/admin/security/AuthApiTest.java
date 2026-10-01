package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import com.rednavis.metaldesk.admin.persistence.UserRepository;
import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.persistence.document.UserDocument;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Signing in as a back-office user, end to end against the migrated database: the two users the
 * first migration creates, the failure cases, and that a token from sign-in works and nothing else
 * does.
 */
class AuthApiTest extends AdminTestSupport {

  private static final String ROLE_PATH = "$.role";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String SIGN_IN = "/api/admin/auth/sign-in";
  private static final String ADMIN = "admin";
  private static final String MANAGER = "manager";

  @Autowired private UserRepository users;
  @Autowired private PasswordEncoder encoder;

  /** Removes the users these tests add; the migrated ones stay. */
  @AfterEach
  void removeExtraUsers() {
    users.findByLogin("disabled-user").ifPresent(users::delete);
  }

  private ResponseSpec signIn(String json) {
    return client.post().uri(SIGN_IN).contentType(MediaType.APPLICATION_JSON).body(json).exchange();
  }

  private static String credentials(String login, String password) {
    return "{\"login\":\"" + login + "\",\"password\":\"" + password + "\"}";
  }

  private String tokenOf(String login, String password) {
    final byte[] body =
        Objects.requireNonNull(
            signIn(credentials(login, password))
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody());
    final String json = new String(body, StandardCharsets.UTF_8);
    return json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
  }

  @Test
  void migratedAdminSignsInWithAdminRole() {
    signIn(credentials(ADMIN, ADMIN))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.tokenType")
        .isEqualTo("Bearer")
        .jsonPath(ROLE_PATH)
        .isEqualTo("ADMIN")
        .jsonPath("$.login")
        .isEqualTo(ADMIN)
        .jsonPath("$.expiresInSeconds")
        .isEqualTo(1800);
  }

  @Test
  void migratedManagerSignsInWithManagerRole() {
    signIn(credentials(MANAGER, MANAGER))
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath(ROLE_PATH)
        .isEqualTo("MANAGER");
  }

  @Test
  void loginIsNotCaseSensitive() {
    signIn(credentials(" Admin ", "admin")).expectStatus().isOk();
  }

  @Test
  void tokenFromSignInOpensTheApiAndNamesTheUser() {
    final String token = tokenOf(MANAGER, MANAGER);

    client
        .get()
        .uri("/api/admin/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.login")
        .isEqualTo("manager")
        .jsonPath("$.email")
        .isEqualTo("manager@manager.by")
        .jsonPath(ROLE_PATH)
        .isEqualTo("MANAGER");
    client
        .get()
        .uri("/api/admin/tiers")
        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void storedPasswordIsHashedNeverPlain() {
    final String hash = users.findByLogin(ADMIN).orElseThrow().passwordHash();
    Assertions.assertNotEquals(ADMIN, hash);
    Assertions.assertTrue(encoder.matches(ADMIN, hash));
  }

  @Test
  void everyFailureLooksTheSame() {
    final String wrongPassword =
        signIn(credentials(ADMIN, "nope"))
            .expectStatus()
            .isUnauthorized()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    final String unknownLogin =
        signIn(credentials("nobody", "admin"))
            .expectStatus()
            .isUnauthorized()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

    Assertions.assertTrue(wrongPassword.contains("\"code\":\"auth.invalid-credentials\""));
    Assertions.assertEquals(
        wrongPassword.replaceAll("\"correlationId\":\"[^\"]*\"", ""),
        unknownLogin.replaceAll("\"correlationId\":\"[^\"]*\"", ""));
  }

  @Test
  void missingBodyOrFieldIsTheSameFailure() {
    client.post().uri(SIGN_IN).exchange().expectStatus().isUnauthorized();
    signIn("{}").expectStatus().isUnauthorized();
    signIn("{\"login\":\"admin\"}").expectStatus().isUnauthorized();
    signIn(credentials(ADMIN, "")).expectStatus().isUnauthorized();
  }

  @Test
  void passwordLongerThanBcryptReadsIsRefusedNotTruncated() {
    signIn(credentials(ADMIN, ADMIN + "x".repeat(80))).expectStatus().isUnauthorized();
  }

  @Test
  void disabledUserCannotSignIn() {
    users.save(
        new UserDocument(
            "disabled-1",
            "disabled-user",
            "disabled@example.com",
            encoder.encode("secret"),
            UserRole.MANAGER,
            false,
            Instant.now()));

    signIn(credentials("disabled-user", "secret")).expectStatus().isUnauthorized();
  }

  @Test
  void customerCannotSignInToTheBackOffice() {
    customers.save(
        new CustomerDocument(
            "cust-1",
            "Carla Customer",
            "customer@example.com",
            null,
            List.of(),
            VerificationState.VERIFIED));

    signIn(credentials("customer@example.com", "whatever")).expectStatus().isUnauthorized();
  }

  @Test
  void refreshSwapsTokenForNewOneThatWorks() {
    final String old = tokenOf(MANAGER, MANAGER);

    final byte[] body =
        Objects.requireNonNull(
            client
                .post()
                .uri("/api/admin/auth/refresh")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + old)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.expiresInSeconds")
                .isEqualTo(1800)
                .jsonPath(ROLE_PATH)
                .isEqualTo("MANAGER")
                .returnResult()
                .getResponseBody());
    final String fresh =
        new String(body, StandardCharsets.UTF_8)
            .replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    client
        .get()
        .uri("/api/admin/me")
        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + fresh)
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void refreshNeedsToken() {
    client.post().uri("/api/admin/auth/refresh").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void refreshIsRefusedForUserWhoNoLongerExists() {
    client
        .post()
        .uri("/api/admin/auth/refresh")
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.ADMIN))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void userWhoNoLongerExistsIsToldTheyAreSignedOut() {
    // A valid token whose subject is in no database: the signature holds, the user does not.
    client
        .get()
        .uri("/api/admin/me")
        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.ADMIN))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void tooManyFailuresLockTheAttemptOut() {
    for (int attempt = 0; attempt < 5; attempt++) {
      signIn(credentials("lockme", "wrong")).expectStatus().isUnauthorized();
    }

    signIn(credentials("lockme", "wrong"))
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .exists(HttpHeaders.RETRY_AFTER)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("auth.throttled");
  }

  @Test
  void lockOnOneLoginDoesNotLockAnother() {
    for (int attempt = 0; attempt < 5; attempt++) {
      signIn(credentials("locked-too", "wrong")).expectStatus().isUnauthorized();
    }

    signIn(credentials(ADMIN, ADMIN)).expectStatus().isOk();
  }

  @Test
  void signOutAlwaysConfirms() {
    client.post().uri("/api/admin/auth/sign-out").exchange().expectStatus().isNoContent();
  }
}
