package com.rednavis.metaldesk.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.rednavis.metaldesk.admin.AdminTestSupport;
import com.rednavis.metaldesk.share.domain.user.UserRole;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Authentication is the staff token and nothing else: every route needs one, and a token that is
 * not exactly a staff token (wrong audience, wrong issuer, wrong key, expired, missing claims) is
 * worth nothing here, even when it is signed with this service's own key.
 */
class StaffAuthenticationTest extends AdminTestSupport {

  private static final String SEGMENT = "x";
  private static final List<String> PUBLIC =
      List.of("/api/admin/auth/sign-in", "/api/admin/auth/sign-out");

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Autowired private SecretKey staffSigningKey;
  @Autowired private JwtProperties properties;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String BEARER = "Bearer ";

  private static SecretKey randomKey() {
    final byte[] key = new byte[32];
    RANDOM.nextBytes(key);
    return new SecretKeySpec(key, "HmacSHA256");
  }

  private static String sign(SecretKey key, JwtClaimsSet claims) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private JwtClaimsSet.Builder staffClaims() {
    final Instant now = Instant.now();
    return JwtClaimsSet.builder()
        .issuer(properties.issuer())
        .audience(List.of(properties.audience()))
        .subject("user-1")
        .issuedAt(now)
        .expiresAt(now.plusSeconds(600))
        .claim(TokenIssuer.LOGIN_CLAIM, "staff")
        .claim(TokenIssuer.ROLE_CLAIM, "MANAGER");
  }

  private List<String[]> protectedEndpoints() {
    return mappings.getHandlerMethods().keySet().stream()
        .filter(info -> info.getPathPatternsCondition() != null)
        .flatMap(
            info ->
                Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues().stream()
                    .filter(path -> path.startsWith("/api/admin") && !PUBLIC.contains(path))
                    .flatMap(
                        path -> methods(info).stream().map(method -> new String[] {method, path})))
        .toList();
  }

  private static List<String> methods(RequestMappingInfo info) {
    return info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
  }

  private static String concrete(String path) {
    return path.replaceAll("\\{[^}]+}", SEGMENT);
  }

  private void assertEveryEndpointRefuses(String authorization) {
    for (final String[] endpoint : protectedEndpoints()) {
      final RestTestClient.RequestHeadersSpec<?> request =
          client.method(HttpMethod.valueOf(endpoint[0])).uri(concrete(endpoint[1]));
      (authorization == null ? request : request.header("Authorization", authorization))
          .exchange()
          .expectStatus()
          .isUnauthorized()
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("auth.unauthorized");
    }
  }

  @Test
  void endpointListIsNotEmpty() {
    assertFalse(protectedEndpoints().isEmpty());
  }

  @Test
  void everyAdminEndpointRefusesRequestWithNoToken() {
    assertEveryEndpointRefuses(null);
  }

  @Test
  void everyAdminEndpointRefusesStorefrontTokenEvenSignedWithTheStaffKey() {
    final String customer =
        sign(
            staffSigningKey,
            JwtClaimsSet.builder()
                .issuer("metal-desk-api")
                .audience(List.of("metal-desk-storefront"))
                .subject("cust-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("verification", "VERIFIED")
                .build());
    assertEveryEndpointRefuses(BEARER + customer);
  }

  @Test
  void everyAdminEndpointRefusesTokenForAnotherAudience() {
    assertEveryEndpointRefuses(
        BEARER
            + sign(
                staffSigningKey, staffClaims().audience(List.of("metal-desk-storefront")).build()));
  }

  @Test
  void everyAdminEndpointRefusesTokenFromAnotherIssuer() {
    assertEveryEndpointRefuses(
        BEARER + sign(staffSigningKey, staffClaims().issuer("metal-desk-api").build()));
  }

  @Test
  void everyAdminEndpointRefusesTokenSignedWithAnotherKey() {
    assertEveryEndpointRefuses(BEARER + sign(randomKey(), staffClaims().build()));
  }

  @Test
  void everyAdminEndpointRefusesExpiredToken() {
    final Instant past = Instant.now().minusSeconds(3600);
    assertEveryEndpointRefuses(
        BEARER
            + sign(
                staffSigningKey,
                staffClaims().issuedAt(past.minusSeconds(60)).expiresAt(past).build()));
  }

  @Test
  void everyAdminEndpointRefusesTokenWithUnknownRole() {
    assertEveryEndpointRefuses(
        BEARER + sign(staffSigningKey, staffClaims().claim("role", "SUPERUSER").build()));
  }

  @Test
  void everyAdminEndpointRefusesTokenWithoutLogin() {
    assertEveryEndpointRefuses(
        BEARER + sign(staffSigningKey, staffClaims().claim("login", "").build()));
  }

  @Test
  void everyAdminEndpointRefusesGarbage() {
    assertEveryEndpointRefuses("Bearer not-a-token");
  }

  @Test
  void unknownRouteIsRefusedBeforeItIsLookedUp() {
    client.get().uri("/api/admin/nothing-here").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void validTokenOfEitherRoleIsAccepted() {
    for (final UserRole role : UserRole.values()) {
      client
          .get()
          .uri("/api/admin/tiers")
          .header("Authorization", bearer(role))
          .exchange()
          .expectStatus()
          .isOk();
    }
  }

  @Test
  void healthStaysOpen() {
    client.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }
}
