package com.rednavis.metaldesk.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The externally configured settings of the access token, bound from {@code metaldesk.jwt}.
 *
 * <p>The signing key is never committed. A deployment supplies it through {@code JWT_SIGNING_KEY}
 * (from Secret Manager, Architecture section 7) as Base64 of at least 32 random bytes. When it is
 * empty, as in local development and tests, a random key is generated at startup, so tokens do not
 * survive a restart and are not valid on another instance.
 *
 * @param issuer the {@code iss} claim every token carries and every validation requires
 * @param audience the {@code aud} claim every token carries and every validation requires; it names
 *     the environment's storefront, so a token minted for another environment is refused
 * @param ttl how long a token lives, short by design because there is no revocation
 * @param signingKey the Base64 HMAC key, or empty to generate one at startup
 */
@ConfigurationProperties("metaldesk.jwt")
public record JwtProperties(
    @DefaultValue("metal-desk-api") String issuer,
    @DefaultValue("metal-desk-storefront") String audience,
    @DefaultValue("30m") Duration ttl,
    @DefaultValue("") String signingKey) {

  /** Redacts the key, so logged properties do not leak it. */
  @Override
  public String toString() {
    return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", ttl=" + ttl + ']';
  }
}
