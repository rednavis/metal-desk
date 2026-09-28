package com.rednavis.metaldesk.payments.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WalletConfigurationTest {

  @Test
  void defaultsAreTenSecondsAndOneRetryAndTrailingSlashIsDropped() {
    final WalletConfiguration configuration =
        WalletConfiguration.withDefaults(URI.create("https://wallet.example/"));
    assertEquals(Duration.ofSeconds(10), configuration.timeout());
    assertEquals(1, configuration.retryBudget());
    assertEquals(URI.create("https://wallet.example"), configuration.baseUrl());
  }

  @Test
  void invalidSettingsAreRefusedByTheSharedEndpointRules() {
    final URI relative = URI.create("/relative");
    final URI base = URI.create("https://wallet.example");
    assertEquals(
        "http-endpoint.base-url-invalid",
        assertThrows(
                ValidationException.class,
                () -> new WalletConfiguration(relative, Duration.ofSeconds(1), 0))
            .code());
    assertEquals(
        "http-endpoint.timeout-invalid",
        assertThrows(
                ValidationException.class, () -> new WalletConfiguration(base, Duration.ZERO, 0))
            .code());
    assertEquals(
        "http-endpoint.retry-budget-invalid",
        assertThrows(
                ValidationException.class,
                () -> new WalletConfiguration(base, Duration.ofSeconds(1), -1))
            .code());
  }
}
