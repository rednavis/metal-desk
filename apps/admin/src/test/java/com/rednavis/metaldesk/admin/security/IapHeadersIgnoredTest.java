package com.rednavis.metaldesk.admin.security;

import com.rednavis.metaldesk.admin.AdminTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * The application authenticates only with its own bearer token (ADR-0006). Identity-Aware Proxy's
 * headers, which anything that can reach the service directly can forge, must never authenticate
 * anyone or choose who a signed-in user is.
 */
class IapHeadersIgnoredTest extends AdminTestSupport {

  private static final String EMAIL_HEADER = "X-Goog-Authenticated-User-Email";
  private static final String ASSERTION_HEADER = "X-Goog-IAP-JWT-Assertion";
  private static final String FORGED_EMAIL = "accounts.google.com:attacker@example.com";
  private static final String FAKE_ASSERTION = "not.a.signed.assertion";

  private String managerToken() {
    final byte[] body =
        Objects.requireNonNull(
            client
                .post()
                .uri("/api/admin/auth/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"login\":\"manager\",\"password\":\"manager\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .returnResult()
                .getResponseBody());
    return new String(body, StandardCharsets.UTF_8)
        .replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
  }

  @Test
  void forgedIdentityHeaderWithNoTokenIsRejected() {
    for (final String path : new String[] {"/api/admin/me", "/api/admin/tiers"}) {
      client
          .get()
          .uri(path)
          .header(EMAIL_HEADER, FORGED_EMAIL)
          .exchange()
          .expectStatus()
          .isUnauthorized();
    }
  }

  @Test
  void assertionHeaderWithNoTokenIsRejectedWhateverItHolds() {
    client
        .get()
        .uri("/api/admin/me")
        .header(ASSERTION_HEADER, FAKE_ASSERTION)
        .header(EMAIL_HEADER, FORGED_EMAIL)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void forgedHeadersLeaveTheSignedInUserUnchanged() {
    client
        .get()
        .uri("/api/admin/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken())
        .header(EMAIL_HEADER, FORGED_EMAIL)
        .header(ASSERTION_HEADER, FAKE_ASSERTION)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.login")
        .isEqualTo("manager");
  }
}
