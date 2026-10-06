package com.rednavis.metaldesk.api.payments;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the payment adapters talk to, bound from {@code metaldesk.payments}. In development and CI
 * the addresses point at WireMock stubs (ADR-0002); a deployment points them at real providers. No
 * credential belongs here.
 *
 * @param gateway the card and bank gateway
 * @param wallet the account-based wallet provider
 */
@ConfigurationProperties("metaldesk.payments")
public record PaymentsProperties(@DefaultValue Endpoint gateway, @DefaultValue Endpoint wallet) {

  /**
   * One provider's endpoint.
   *
   * @param baseUrl the provider's base address
   * @param timeout how long to wait for one call
   * @param retryBudget how many extra attempts an idempotent call may make (an authorisation is
   *     never retried)
   * @param stub whether to answer with a canned {@code captured} response and make no request
   */
  public record Endpoint(
      @DefaultValue("http://localhost:9091") String baseUrl,
      @DefaultValue("10s") Duration timeout,
      @DefaultValue("1") int retryBudget,
      @DefaultValue("false") boolean stub) {}
}
