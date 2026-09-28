package com.rednavis.metaldesk.payments.wallet;

import com.rednavis.metaldesk.payments.http.HttpEndpoint;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;

/**
 * Everything about reaching the wallet provider that a deployment decides: where it is, how long to
 * wait, and how often to retry an idempotent call. It is the wallet's own configuration type, so a
 * container can bind the wallet and the gateway separately; the rules are those of {@link
 * HttpEndpoint}.
 *
 * <p>All of it is supplied from outside, and nothing in this package names a host (ADR-0002). The
 * retry budget applies to {@code confirm} only; taking a payment is never retried, because a
 * timed-out request may have succeeded and taking the money twice is worse than a failed checkout.
 *
 * @param baseUrl where the wallet provider is, absolute with an {@code http} or {@code https}
 *     scheme and a host; a trailing slash is dropped
 * @param timeout how long to wait for one call before it counts as timed out, greater than zero and
 *     at most {@value HttpEndpoint#MAX_TIMEOUT_SECONDS} seconds
 * @param retryBudget how many extra attempts a {@code confirm} call may make after a transport
 *     failure, from 0 to {@value HttpEndpoint#MAX_RETRY_BUDGET}
 */
public record WalletConfiguration(URI baseUrl, Duration timeout, int retryBudget) {

  /**
   * Validates the fields (see {@link HttpEndpoint}) and drops a trailing slash from the base URL.
   *
   * @throws ValidationException if the base URL, timeout or retry budget is not valid
   */
  public WalletConfiguration {
    baseUrl = new HttpEndpoint(baseUrl, timeout, retryBudget).baseUrl();
  }

  /**
   * Builds a configuration for a base URL with the default timeout (10 seconds) and retry budget
   * (1).
   *
   * @param baseUrl where the wallet provider is
   * @return the configuration
   * @throws ValidationException if the base URL is not valid
   */
  public static WalletConfiguration withDefaults(URI baseUrl) {
    final HttpEndpoint endpoint = HttpEndpoint.withDefaults(baseUrl);
    return new WalletConfiguration(endpoint.baseUrl(), endpoint.timeout(), endpoint.retryBudget());
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
