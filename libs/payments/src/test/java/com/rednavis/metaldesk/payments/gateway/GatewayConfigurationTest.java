package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GatewayConfigurationTest {

  private static final URI BASE = URI.create("https://gateway.example");

  @Test
  void defaultsAreTenSecondsAndOneRetry() {
    final GatewayConfiguration configuration = GatewayConfiguration.withDefaults(BASE);
    assertEquals(Duration.ofSeconds(10), configuration.timeout());
    assertEquals(1, configuration.retryBudget());
  }

  @Test
  void trailingSlashIsDroppedAndPathsResolveAgainstBase() {
    final GatewayConfiguration configuration =
        new GatewayConfiguration(
            URI.create("https://gateway.example/api/"), Duration.ofSeconds(1), 0);
    assertEquals(URI.create("https://gateway.example/api"), configuration.baseUrl());
    assertEquals(
        URI.create("https://gateway.example/api/v1/payments"),
        configuration.resolve("/v1/payments"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/relative", "ftp://gateway.example", "file:///tmp", "https:///nohost"})
  void baseUrlMustBeAbsoluteWebUriWithHost(String uri) {
    final URI bad = URI.create(uri);
    assertEquals(
        "gateway-configuration.base-url-invalid",
        assertThrows(
                ValidationException.class,
                () -> new GatewayConfiguration(bad, Duration.ofSeconds(1), 0))
            .code());
  }

  @Test
  void missingBaseUrlIsRefused() {
    assertEquals(
        "gateway-configuration.base-url-invalid",
        assertThrows(
                ValidationException.class,
                () -> new GatewayConfiguration(null, Duration.ofSeconds(1), 0))
            .code());
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "PT-1S", "PT61S"})
  void timeoutMustBePositiveAndAtMostTheLimit(String iso) {
    final Duration bad = Duration.parse(iso);
    assertEquals(
        "gateway-configuration.timeout-invalid",
        assertThrows(ValidationException.class, () -> new GatewayConfiguration(BASE, bad, 0))
            .code());
  }

  @Test
  void missingTimeoutIsRefused() {
    assertEquals(
        "gateway-configuration.timeout-invalid",
        assertThrows(ValidationException.class, () -> new GatewayConfiguration(BASE, null, 0))
            .code());
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, GatewayConfiguration.MAX_RETRY_BUDGET + 1})
  void retryBudgetMustBeInRange(int budget) {
    assertEquals(
        "gateway-configuration.retry-budget-invalid",
        assertThrows(
                ValidationException.class,
                () -> new GatewayConfiguration(BASE, Duration.ofSeconds(1), budget))
            .code());
  }
}
