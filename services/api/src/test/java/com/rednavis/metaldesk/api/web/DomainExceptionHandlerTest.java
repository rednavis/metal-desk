package com.rednavis.metaldesk.api.web;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

/** Every failure becomes the one envelope, with the domain's own code and nothing internal. */
class DomainExceptionHandlerTest {

  private static final String CODE = "$.code";
  private static final String MISSING = "/missing";

  private final WebTestClient client =
      WebTestClient.bindToController(new FailingController())
          .controllerAdvice(new DomainExceptionHandler())
          .build();

  @Test
  void validationIs400WithItsCode() {
    client
        .get()
        .uri("/validation")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("thing.invalid")
        .jsonPath("$.message")
        .isEqualTo("bad thing")
        .jsonPath("$.correlationId")
        .isNotEmpty();
  }

  @Test
  void notFoundIs404WithItsCode() {
    client
        .get()
        .uri(MISSING)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("thing.not-found");
  }

  @Test
  void conflictIs409WithItsCode() {
    client
        .get()
        .uri("/conflict")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("thing.conflict");
  }

  @Test
  void unexpectedFailureIs500AndRevealsNothing() {
    client
        .get()
        .uri("/boom")
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(String.class)
        .value(
            body -> {
              Assertions.assertTrue(body.contains("internal-error"));
              Assertions.assertFalse(body.contains("secret detail"));
              Assertions.assertFalse(body.contains("RuntimeException"));
            });
  }

  @Test
  void unreadableParameterIs400() {
    client
        .get()
        .uri("/number?n=abc")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath(CODE)
        .isEqualTo("request.invalid");
  }

  @Test
  void safeCorrelationIdIsEchoedAndUnsafeOneIsReplaced() {
    client
        .get()
        .uri(MISSING)
        .header("X-Correlation-Id", "abc-123")
        .exchange()
        .expectBody()
        .jsonPath("$.correlationId")
        .isEqualTo("abc-123");
    client
        .get()
        .uri(MISSING)
        .header("X-Correlation-Id", "bad id\twith spaces")
        .exchange()
        .expectBody()
        .jsonPath("$.correlationId")
        .value(id -> Assertions.assertNotEquals("bad id\twith spaces", id));
  }
}
