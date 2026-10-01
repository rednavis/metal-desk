package com.rednavis.metaldesk.admin.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The settings of the staff access token, bound from {@code metaldesk.admin.jwt}.
 *
 * <p>These are deliberately not the storefront's: a different default issuer and audience, and a
 * key of its own, so a customer token fails validation here even if the two services were ever
 * configured with the same secret. The signing key is never committed; a deployment supplies it
 * through {@code ADMIN_JWT_SIGNING_KEY} as Base64 of at least 32 random bytes. When it is empty, as
 * in local development and tests, a random key is generated at startup.
 *
 * @param issuer the {@code iss} claim every token carries and every validation requires
 * @param audience the {@code aud} claim every token carries and every validation requires
 * @param ttl how long a token lives, short by design because there is no revocation
 * @param signingKey the Base64 HMAC key, or empty to generate one at startup
 */
@ConfigurationProperties("metaldesk.admin.jwt")
public record JwtProperties(
    @DefaultValue("metal-desk-admin") String issuer,
    @DefaultValue("metal-desk-staff") String audience,
    @DefaultValue("30m") Duration ttl,
    @DefaultValue("") String signingKey) {

  /** Redacts the key, so logged properties do not leak it. */
  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", ttl=" + ttl + ']';
  }
}
