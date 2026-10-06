package com.rednavis.metaldesk.api.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rednavis.metaldesk.api.persistence.MongoTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The storefront is for customers only. The back-office users the first migration creates exist in
 * the same database, and none of them can sign in here, nor can anything a back-office token looks
 * like get past the token check, even when it is signed with this service's own key.
 */
@AutoConfigureWebTestClient
class StaffSeparationTest extends MongoTestSupport {

  private static final String ADMIN = "admin";
  private static final String ROLE = "role";
  private static final String LOGIN = "login";

  @Autowired private WebTestClient client;
  @Autowired private SecretKey jwtSigningKey;
  @Autowired private JwtProperties properties;

  private WebTestClient.ResponseSpec signIn(String identifier, String password) {
    return client
        .post()
        .uri("/api/auth/sign-in")
        .bodyValue(Map.of("identifier", identifier, "password", password))
        .exchange();
  }

  private String token(JwtClaimsSet claims) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Test
  void backOfficeUserCannotSignInByEmail() {
    signIn("admin@admin.by", ADMIN).expectStatus().isUnauthorized();
    signIn("manager@manager.by", "manager").expectStatus().isUnauthorized();
  }

  @Test
  void backOfficeLoginIsNotStorefrontIdentifier() {
    signIn(ADMIN, ADMIN).expectStatus().isUnauthorized();
  }

  @Test
  void staffTokenIsRefusedForItsAudience() {
    final Instant now = Instant.now();
    final String staff =
        token(
            JwtClaimsSet.builder()
                .issuer("metal-desk-admin")
                .audience(List.of("metal-desk-staff"))
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim(LOGIN, ADMIN)
                .claim(ROLE, "ADMIN")
                .build());

    client
        .get()
        .uri("/api/auth/me")
        .header("Authorization", "Bearer " + staff)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void staffTokenIsRefusedEvenWithTheStorefrontIssuerAndAudience() {
    final Instant now = Instant.now();
    final String staff =
        token(
            JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .claim(LOGIN, ADMIN)
                .claim(ROLE, "ADMIN")
                .build());

    client
        .get()
        .uri("/api/auth/me")
        .header("Authorization", "Bearer " + staff)
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }
}
