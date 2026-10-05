package com.rednavis.metaldesk.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** A decoded token is accepted only with a subject, a login and a role this service knows. */
class StaffTokenConverterTest {

  private static final String SUBJECT = "u-1";
  private static final String LOGIN = "admin";
  private static final String ROLE = "ADMIN";

  private final StaffTokenConverter converter = new StaffTokenConverter();

  private static Jwt token(String subject, String login, String role) {
    final Jwt.Builder builder =
        Jwt.withTokenValue("t")
            .header("alg", "HS256")
            .issuedAt(Instant.EPOCH)
            .expiresAt(Instant.EPOCH.plusSeconds(60));
    if (subject != null) {
      builder.subject(subject);
    }
    if (login != null) {
      builder.claim(TokenIssuer.LOGIN_CLAIM, login);
    }
    if (role != null) {
      builder.claim(TokenIssuer.ROLE_CLAIM, role);
    }
    return builder.claim("scope", "x").build();
  }

  @Test
  void completeTokenBecomesAuthentication() {
    final Authentication authentication = converter.convert(token(SUBJECT, LOGIN, ROLE));
    assertEquals(LOGIN, ((StaffAuthentication) authentication).getPrincipal().login());
  }

  @Test
  void tokenWithoutSubjectLoginOrKnownRoleIsInvalid() {
    assertThrows(
        InvalidBearerTokenException.class, () -> converter.convert(token(null, LOGIN, ROLE)));
    assertThrows(
        InvalidBearerTokenException.class, () -> converter.convert(token(SUBJECT, null, ROLE)));
    assertThrows(
        InvalidBearerTokenException.class, () -> converter.convert(token(SUBJECT, " ", ROLE)));
    assertThrows(
        InvalidBearerTokenException.class, () -> converter.convert(token(SUBJECT, LOGIN, null)));
    assertThrows(
        InvalidBearerTokenException.class,
        () -> converter.convert(token(SUBJECT, LOGIN, "SUPERUSER")));
  }
}
