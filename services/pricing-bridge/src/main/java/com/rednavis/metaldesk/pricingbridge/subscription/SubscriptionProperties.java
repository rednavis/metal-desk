package com.rednavis.metaldesk.pricingbridge.subscription;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the feed subscription, under {@code metaldesk.pricing-bridge.subscription}.
 *
 * @param initialBackoff the delay before the first resubscription
 * @param maxBackoff the largest delay between resubscriptions
 * @param jitter the fraction of a delay that may be shaved off, from 0 up to but excluding 1
 * @param staleAfter how long the feed may be silent before it is reported stale
 */
@ConfigurationProperties("metaldesk.pricing-bridge.subscription")
public record SubscriptionProperties(
    @DefaultValue("1s") Duration initialBackoff,
    @DefaultValue("30s") Duration maxBackoff,
    @DefaultValue("0.5") double jitter,
    @DefaultValue("30s") Duration staleAfter) {

  /**
   * Validates the settings.
   *
   * @throws ValidationException if {@code staleAfter} is not positive, or the backoff is invalid
   */
  public SubscriptionProperties {
    if (staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()) {
      throw new ValidationException("subscription.stale-invalid", "staleAfter must be positive");
    }
    new ReconnectBackoff(initialBackoff, maxBackoff, jitter);
  }

  /**
   * The reconnect policy these settings describe.
   *
   * @return the backoff
   */
  public ReconnectBackoff backoff() {
    return new ReconnectBackoff(initialBackoff, maxBackoff, jitter);
  }
}
