package com.rednavis.metaldesk.api.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The FR-2.2 sign-in throttle settings, bound from {@code metaldesk.auth.throttle}. The defaults
 * are the BRD's illustrative figures.
 *
 * @param maxFailures how many consecutive failures from one source lock it out
 * @param coolDown how long a locked source stays locked
 * @param trustedProxies the addresses or CIDR blocks of the reverse proxies allowed to say who the
 *     client is through {@code X-Forwarded-For}; empty means no proxy is trusted
 */
@ConfigurationProperties("metaldesk.auth.throttle")
public record ThrottleProperties(
    @DefaultValue("3") int maxFailures,
    @DefaultValue("15m") Duration coolDown,
    @DefaultValue List<String> trustedProxies) {

  /** Copies the list, so the properties cannot be changed through it. */
  public ThrottleProperties {
    trustedProxies = List.copyOf(trustedProxies);
  }
}
