package com.rednavis.metaldesk.admin.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The sign-in throttle settings, bound from {@code metaldesk.admin.auth.throttle}.
 *
 * @param maxFailures how many consecutive failures for one login from one address lock it out
 * @param coolDown how long a locked login stays locked
 */
@ConfigurationProperties("metaldesk.admin.auth.throttle")
public record ThrottleProperties(
    @DefaultValue("5") int maxFailures, @DefaultValue("15m") Duration coolDown) {}
