package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import tools.jackson.databind.json.JsonMapper;

/** All four checks are made, each is proven on its own, and the payload holds nothing sensitive. */
class JwtValidatorTest {

  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
  private static final AuthenticatedCustomer CUSTOMER =
      new AuthenticatedCustomer(new CustomerId("c-1"), VerificationState.UNVERIFIED);

  private final MutableClock clock = new MutableClock(NOW);
  private final SecretKey key = keyOf("k");
  private final JwtProperties properties =
      new JwtProperties("issuer-a", "audience-a", Duration.ofMinutes(15), "");
  private final JwtValidator validator =
      new JwtValidator(new JwtConfiguration().jwtDecoder(properties, key, clock));

  private static SecretKey keyOf(String seed) {
    return new SecretKeySpec(seed.repeat(32).getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  private String tokenWith(JwtProperties minted, SecretKey signedWith, Instant at) {
    return new JwtIssuer(minted, signedWith, new MutableClock(at)).issue(CUSTOMER).value();
  }

  @Test
  void goodTokenYieldsTheCustomer() {
    final String token = tokenWith(properties, key, NOW);

    assertEquals(CUSTOMER, validator.validate(token).block());
  }

  @Test
  void missingTokenIsRejected() {
    assertThrows(Exception.class, () -> validator.validate(null).block());
    assertThrows(Exception.class, () -> validator.validate("  ").block());
  }

  @Test
  void anExpiredTokenIsRejected() {
    final String token = tokenWith(properties, key, NOW.minus(Duration.ofMinutes(16)));

    assertThrows(JwtValidationException.class, () -> validator.validate(token).block());
  }

  @Test
  void tokenSignedWithAnotherKeyIsRejected() {
    final String token = tokenWith(properties, keyOf("z"), NOW);

    assertThrows(Exception.class, () -> validator.validate(token).block());
  }

  @Test
  void tokenFromAnotherIssuerIsRejected() {
    final JwtProperties other =
        new JwtProperties("issuer-b", "audience-a", Duration.ofMinutes(15), "");

    assertThrows(
        JwtValidationException.class, () -> validator.validate(tokenWith(other, key, NOW)).block());
  }

  @Test
  void tokenForAnotherAudienceIsRejected() {
    final JwtProperties other =
        new JwtProperties("issuer-a", "audience-b", Duration.ofMinutes(15), "");

    assertThrows(
        JwtValidationException.class, () -> validator.validate(tokenWith(other, key, NOW)).block());
  }

  @Test
  void garbageTokenIsRejected() {
    assertThrows(Exception.class, () -> validator.validate("not.a.jwt").block());
  }

  @Test
  void tokenIsRejectedOnceItsLifetimeHasPassedWithNoSkew() {
    final String token = tokenWith(properties, key, NOW);
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));

    assertThrows(JwtValidationException.class, () -> validator.validate(token).block());
  }

  @Test
  void thePayloadHoldsNoEmailPhoneOrHash() {
    final String token = tokenWith(properties, key, NOW);
    final String payload = token.split("\\.")[1];
    final Map<?, ?> claims =
        JsonMapper.builder().build().readValue(Base64.getUrlDecoder().decode(payload), Map.class);

    assertEquals(Set.of("iss", "aud", "sub", "iat", "exp", "verification"), claims.keySet());
    assertEquals("c-1", claims.get("sub"));
    assertEquals("UNVERIFIED", claims.get("verification"));
  }
}
