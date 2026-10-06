package com.rednavis.metaldesk.api.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/** Wires the token signing key and the decoder that validates tokens signed with it. */
@Slf4j
@Configuration
public class JwtConfiguration {

  private static final int MIN_KEY_BYTES = 32;
  private static final String ALGORITHM = "HmacSHA256";
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * Provides the HMAC signing key: the configured one, or a random one generated now.
   *
   * @param properties the token settings
   * @return the key
   * @throws IllegalStateException if a configured key is not Base64 of at least 32 bytes
   */
  @Bean
  public SecretKey jwtSigningKey(JwtProperties properties) {
    final byte[] bytes;
    if (properties.signingKey().isBlank()) {
      bytes = new byte[MIN_KEY_BYTES];
      RANDOM.nextBytes(bytes);
      log.warn(
          "No JWT signing key configured (JWT_SIGNING_KEY): generated a random one. Tokens will"
              + " not survive a restart and are not valid on other instances. Development only.");
    } else {
      try {
        bytes = Base64.getDecoder().decode(properties.signingKey());
      } catch (IllegalArgumentException notBase64) {
        throw new IllegalStateException("metaldesk.jwt.signing-key is not valid Base64", notBase64);
      }
      if (bytes.length < MIN_KEY_BYTES) {
        throw new IllegalStateException(
            "metaldesk.jwt.signing-key must be at least " + MIN_KEY_BYTES + " bytes");
      }
    }
    return new SecretKeySpec(bytes, ALGORITHM);
  }

  /**
   * Builds the decoder that validates bearer tokens: signature, expiry, issuer and audience, with
   * no clock-skew allowance because the lifetime is already short.
   *
   * @param properties the expected issuer and audience
   * @param key the signing key, the same one {@link JwtIssuer} uses
   * @param clock the source of time for the expiry check
   * @return the decoder
   */
  @Bean
  public ReactiveJwtDecoder jwtDecoder(JwtProperties properties, SecretKey key, Clock clock) {
    final JwtTimestampValidator timestamp = new JwtTimestampValidator(Duration.ZERO);
    timestamp.setClock(clock);
    final OAuth2TokenValidator<Jwt> audience =
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, claim -> claim != null && claim.contains(properties.audience()));
    final NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            timestamp, new JwtIssuerValidator(properties.issuer()), audience));
    return decoder;
  }
}
