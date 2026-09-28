package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * Everything about reaching the gateway that a deployment decides: where it is, how long to wait,
 * and how often to retry an idempotent call. All of it is supplied from outside; nothing here or
 * anywhere in this package names a host, so pointing the adapter at a real provider instead of a
 * stub is a configuration change, not a code change (ADR-0002).
 *
 * <p><strong>The retry budget applies to {@code confirm} only.</strong> Confirming a payment asks
 * the gateway for the current state of a payment it already knows, so repeating it after a timeout
 * or a dropped connection is safe. Authorising is never retried after the request may have reached
 * the gateway: a timed-out authorisation may have succeeded, and taking the money twice is worse
 * than a failed checkout.
 *
 * @param baseUrl where the gateway is, absolute with an {@code http} or {@code https} scheme and a
 *     host; a trailing slash is dropped
 * @param timeout how long to wait for one gateway call before it counts as timed out, greater than
 *     zero and at most {@value #MAX_TIMEOUT_SECONDS} seconds
 * @param retryBudget how many extra attempts a {@code confirm} call may make after a transport
 *     failure, from 0 to {@value #MAX_RETRY_BUDGET}
 */
public record GatewayConfiguration(URI baseUrl, Duration timeout, int retryBudget) {

  /** The longest timeout accepted, in seconds. */
  public static final long MAX_TIMEOUT_SECONDS = 60;

  /** The most extra attempts accepted for a {@code confirm} call. */
  public static final int MAX_RETRY_BUDGET = 3;

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int DEFAULT_RETRY_BUDGET = 1;

  /**
   * Validates the fields and drops a trailing slash from the base URL.
   *
   * @throws ValidationException if the base URL is not an absolute {@code http(s)} URI with a host,
   *     the timeout is missing, not positive or too long, or the retry budget is out of range
   */
  public GatewayConfiguration {
    baseUrl = normalise(requireWebUri(baseUrl));
    requireTimeout(timeout);
    requireRetryBudget(retryBudget);
  }

  private static URI requireWebUri(URI uri) {
    final String scheme = uri == null ? "" : String.valueOf(uri.getScheme());
    final String lower = scheme.toLowerCase(Locale.ROOT);
    if (!("http".equals(lower) || "https".equals(lower)) || uri.getHost() == null) {
      throw new ValidationException(
          "gateway-configuration.base-url-invalid",
          "Gateway base URL must be an absolute http or https URI with a host");
    }
    return uri;
  }

  private static URI normalise(URI uri) {
    return URI.create(uri.toString().replaceAll("/+$", ""));
  }

  private static void requireTimeout(Duration timeout) {
    if (timeout == null
        || timeout.isNegative()
        || timeout.isZero()
        || timeout.compareTo(Duration.ofSeconds(MAX_TIMEOUT_SECONDS)) > 0) {
      throw new ValidationException(
          "gateway-configuration.timeout-invalid",
          "Gateway timeout must be above zero and at most " + MAX_TIMEOUT_SECONDS + " seconds");
    }
  }

  private static void requireRetryBudget(int retryBudget) {
    if (retryBudget < 0 || retryBudget > MAX_RETRY_BUDGET) {
      throw new ValidationException(
          "gateway-configuration.retry-budget-invalid",
          "Gateway retry budget must be from 0 to " + MAX_RETRY_BUDGET);
    }
  }

  /**
   * Builds a configuration for a base URL with the default timeout (10 seconds) and retry budget
   * (1).
   *
   * @param baseUrl where the gateway is
   * @return the configuration
   * @throws ValidationException if the base URL is not valid
   */
  public static GatewayConfiguration withDefaults(URI baseUrl) {
    return new GatewayConfiguration(baseUrl, DEFAULT_TIMEOUT, DEFAULT_RETRY_BUDGET);
  }

  /**
   * Resolves a path against the base URL.
   *
   * @param path the path, starting with a slash
   * @return the full URI
   */
  /* default */ URI resolve(String path) {
    return URI.create(baseUrl + path);
  }
}
