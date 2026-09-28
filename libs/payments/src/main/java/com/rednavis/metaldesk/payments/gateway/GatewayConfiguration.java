package com.rednavis.metaldesk.payments.gateway;

import com.rednavis.metaldesk.payments.http.HttpEndpoint;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;

/**
 * Everything about reaching the gateway that a deployment decides: where it is, how long to wait,
 * and how often to retry an idempotent call. It is the gateway's own configuration type, so a
 * container can bind the gateway and the wallet separately; the rules are those of {@link
 * HttpEndpoint}.
 *
 * <p>All of it is supplied from outside. Nothing here or anywhere in this package names a host, so
 * pointing the adapter at a real provider instead of a stub is a configuration change, not a code
 * change (ADR-0002).
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
 *     zero and at most {@value HttpEndpoint#MAX_TIMEOUT_SECS} seconds
 * @param retryBudget how many extra attempts a {@code confirm} call may make after a transport
 *     failure, from 0 to {@value HttpEndpoint#MAX_RETRY_BUDGET}
 */
public record GatewayConfiguration(URI baseUrl, Duration timeout, int retryBudget) {

  /**
   * Validates the fields (see {@link HttpEndpoint}) and drops a trailing slash from the base URL.
   *
   * @throws ValidationException if the base URL, timeout or retry budget is not valid
   */
  public GatewayConfiguration {
    baseUrl = new HttpEndpoint(baseUrl, timeout, retryBudget).baseUrl();
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
    final HttpEndpoint endpoint = HttpEndpoint.withDefaults(baseUrl);
    return new GatewayConfiguration(endpoint.baseUrl(), endpoint.timeout(), endpoint.retryBudget());
  }

  /**
   * Returns the shared transport settings this configuration describes.
   *
   * @return the endpoint
   */
  /* default */ HttpEndpoint endpoint() {
    return new HttpEndpoint(baseUrl, timeout, retryBudget);
  }
}
