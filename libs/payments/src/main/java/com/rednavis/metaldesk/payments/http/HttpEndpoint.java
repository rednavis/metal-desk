package com.rednavis.metaldesk.payments.http;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/**
 * Where an external provider is and how patient to be with it: the part of every adapter's
 * configuration that is the same whichever provider it is.
 *
 * <p>All of it is supplied from outside. Nothing in this module names a host, so pointing an
 * adapter at a real provider instead of a stub is a configuration change, not a code change
 * (ADR-0002).
 *
 * <p><strong>The retry budget is for idempotent calls only.</strong> {@link JsonHttpClient} applies
 * it to calls the adapter declares safe to repeat, such as confirming a payment it already knows.
 * It is never applied to taking a payment: a timed-out authorisation may have succeeded, and taking
 * the money twice is worse than a failed checkout.
 *
 * @param baseUrl where the provider is, absolute with an {@code http} or {@code https} scheme and a
 *     host; a trailing slash is dropped
 * @param timeout how long to wait for one call before it counts as timed out, greater than zero and
 *     at most {@value #MAX_TIMEOUT_SECS} seconds
 * @param retryBudget how many extra attempts an idempotent call may make after a transport failure,
 *     from 0 to {@value #MAX_RETRY_BUDGET}
 */
public record HttpEndpoint(URI baseUrl, Duration timeout, int retryBudget) {

  /** The longest timeout accepted, in seconds. */
  public static final long MAX_TIMEOUT_SECS = 60;

  /** The most extra attempts accepted for an idempotent call. */
  public static final int MAX_RETRY_BUDGET = 3;

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
  private static final int DEFAULT_RETRIES = 1;

  /**
   * Validates the fields and drops a trailing slash from the base URL.
   *
   * @throws ValidationException if the base URL is not an absolute {@code http(s)} URI with a host,
   *     the timeout is missing, not positive or too long, or the retry budget is out of range
   */
  public HttpEndpoint {
    baseUrl = normalise(requireWebUri(baseUrl));
    requireTimeout(timeout);
    requireRetryBudget(retryBudget);
  }

  /**
   * Builds an endpoint for a base URL with the default timeout (10 seconds) and retry budget (1).
   *
   * @param baseUrl where the provider is
   * @return the endpoint
   * @throws ValidationException if the base URL is not valid
   */
  public static HttpEndpoint withDefaults(URI baseUrl) {
    return new HttpEndpoint(baseUrl, DEFAULT_TIMEOUT, DEFAULT_RETRIES);
  }

  /**
   * Resolves a path against the base URL.
   *
   * @param path the path, starting with a slash
   * @return the full URI
   */
  public URI resolve(String path) {
    return URI.create(baseUrl + path);
  }

  private static URI requireWebUri(URI uri) {
    final String scheme = uri == null ? "" : String.valueOf(uri.getScheme());
    final String lower = scheme.toLowerCase(Locale.ROOT);
    if (!("http".equals(lower) || "https".equals(lower)) || uri.getHost() == null) {
      throw new ValidationException(
          "http-endpoint.base-url-invalid",
          "Base URL must be an absolute http or https URI with a host");
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
        || timeout.compareTo(Duration.ofSeconds(MAX_TIMEOUT_SECS)) > 0) {
      throw new ValidationException(
          "http-endpoint.timeout-invalid",
          "Timeout must be above zero and at most " + MAX_TIMEOUT_SECS + " seconds");
    }
  }

  private static void requireRetryBudget(int retryBudget) {
    if (retryBudget < 0 || retryBudget > MAX_RETRY_BUDGET) {
      throw new ValidationException(
          "http-endpoint.retry-budget-invalid",
          "Retry budget must be from 0 to " + MAX_RETRY_BUDGET);
    }
  }
}
