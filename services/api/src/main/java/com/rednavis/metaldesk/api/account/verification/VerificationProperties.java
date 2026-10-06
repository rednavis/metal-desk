package com.rednavis.metaldesk.api.account.verification;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The verification settings, bound from {@code metaldesk.account.verification}.
 *
 * @param ttl how long a typed verification code lives
 * @param resetTtl how long a password-reset link lives (BRD FR-2.4: time-limited)
 * @param maxAttempts how many confirmation attempts a challenge allows before it is dead
 * @param linkBase the address of the page a reset link points at; the reference and code are
 *     appended as query parameters
 */
@ConfigurationProperties("metaldesk.account.verification")
public record VerificationProperties(
    @DefaultValue("15m") Duration ttl,
    @DefaultValue("30m") Duration resetTtl,
    @DefaultValue("5") int maxAttempts,
    @DefaultValue("http://localhost:5173/reset-password") String linkBase) {

  /**
   * The lifetime for a purpose.
   *
   * @param purpose the purpose
   * @return {@code resetTtl} for a password reset, {@code ttl} otherwise
   */
  public Duration ttlFor(VerificationPurpose purpose) {
    return purpose == VerificationPurpose.PASSWORD_RESET ? resetTtl : ttl;
  }
}
