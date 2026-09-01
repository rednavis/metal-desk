package com.rednavis.metaldesk.pricingbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.config.InfrastructureConfiguration;
import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.pricingbridge.feed.fake.FakeFeedProperties;
import com.rednavis.metaldesk.pricingbridge.subscription.ReconnectBackoff;
import com.rednavis.metaldesk.pricingbridge.subscription.SubscriptionProperties;
import com.rednavis.metaldesk.pricingbridge.web.ApiExceptionHandler;
import com.rednavis.metaldesk.pricingbridge.web.ErrorEnvelope;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.ResponseEntity;

/** What the settings, the tick and the infrastructure beans refuse or provide. */
class ConfigurationValidationTest {

  private static final Duration TWO = Duration.ofSeconds(2);
  private static final Map<Metal, BigDecimal> PRICES = Map.of(Metal.GOLD, new BigDecimal("60"));
  private static final BigDecimal HALF_PERCENT = new BigDecimal("0.005");
  private static final Money PRICE = Money.of("60.00", Currency.EUR);

  private static String codeOf(Executable action) {
    return assertThrows(ValidationException.class, action).code();
  }

  private static FakeFeedProperties feed(
      Duration interval, Map<Metal, BigDecimal> prices, BigDecimal volatility) {
    return new FakeFeedProperties(interval, prices, volatility, 1L);
  }

  @Test
  void fakeFeedRefusesBadIntervals() {
    assertEquals("fake-feed.interval-invalid", codeOf(() -> feed(null, PRICES, HALF_PERCENT)));
    assertEquals(
        "fake-feed.interval-invalid", codeOf(() -> feed(Duration.ZERO, PRICES, HALF_PERCENT)));
    assertEquals(
        "fake-feed.interval-invalid",
        codeOf(() -> feed(Duration.ofSeconds(-1), PRICES, HALF_PERCENT)));
  }

  @Test
  void fakeFeedRefusesMissingOrNonPositivePrices() {
    assertEquals("fake-feed.prices-missing", codeOf(() -> feed(TWO, null, HALF_PERCENT)));
    assertEquals("fake-feed.prices-missing", codeOf(() -> feed(TWO, Map.of(), HALF_PERCENT)));
    assertEquals(
        "fake-feed.price-invalid",
        codeOf(() -> feed(TWO, Map.of(Metal.GOLD, BigDecimal.ZERO), HALF_PERCENT)));
  }

  @Test
  void fakeFeedRefusesVolatilityOutsideZeroToOne() {
    assertEquals("fake-feed.volatility-invalid", codeOf(() -> feed(TWO, PRICES, null)));
    assertEquals("fake-feed.volatility-invalid", codeOf(() -> feed(TWO, PRICES, BigDecimal.ZERO)));
    assertEquals("fake-feed.volatility-invalid", codeOf(() -> feed(TWO, PRICES, BigDecimal.ONE)));
    assertEquals(PRICES, feed(TWO, PRICES, HALF_PERCENT).startingPrices());
  }

  @Test
  void backoffRefusesBadDurationsAndJitter() {
    assertEquals("backoff.initial-invalid", codeOf(() -> new ReconnectBackoff(null, TWO, 0.5)));
    assertEquals(
        "backoff.initial-invalid", codeOf(() -> new ReconnectBackoff(Duration.ZERO, TWO, 0.5)));
    assertEquals("backoff.max-invalid", codeOf(() -> new ReconnectBackoff(TWO, null, 0.5)));
    assertEquals(
        "backoff.max-invalid", codeOf(() -> new ReconnectBackoff(TWO, Duration.ofSeconds(1), 0.5)));
    assertEquals("backoff.jitter-invalid", codeOf(() -> new ReconnectBackoff(TWO, TWO, -0.1)));
    assertEquals("backoff.jitter-invalid", codeOf(() -> new ReconnectBackoff(TWO, TWO, 1.0)));
  }

  @Test
  void subscriptionRefusesNonPositiveStaleness() {
    assertEquals(
        "subscription.stale-invalid",
        codeOf(() -> new SubscriptionProperties(TWO, TWO, 0.5, null)));
    assertEquals(
        "subscription.stale-invalid",
        codeOf(() -> new SubscriptionProperties(TWO, TWO, 0.5, Duration.ZERO)));
    assertEquals(
        "subscription.stale-invalid",
        codeOf(() -> new SubscriptionProperties(TWO, TWO, 0.5, Duration.ofSeconds(-1))));
  }

  @Test
  void tickRefusesMissingFieldsAndBlankSource() {
    final Instant now = Instant.parse("2026-10-01T00:00:00Z");
    assertEquals(
        "tick.field-missing", codeOf(() -> new ReferencePriceTick(null, PRICE, now, "feed")));
    assertEquals(
        "tick.field-missing", codeOf(() -> new ReferencePriceTick(Metal.GOLD, null, now, "feed")));
    assertEquals(
        "tick.field-missing",
        codeOf(() -> new ReferencePriceTick(Metal.GOLD, PRICE, null, "feed")));
    assertEquals(
        "tick.source-missing", codeOf(() -> new ReferencePriceTick(Metal.GOLD, PRICE, now, " ")));
    assertEquals(
        "tick.source-missing", codeOf(() -> new ReferencePriceTick(Metal.GOLD, PRICE, now, null)));
  }

  @Test
  void infrastructureProvidesUtcClockAndRandomSource() {
    final InfrastructureConfiguration configuration = new InfrastructureConfiguration();
    assertEquals("Z", configuration.clock().getZone().getId());
    assertNotNull(configuration.jitterRandom());
  }

  @Test
  void unexpectedFailureIsOpaque500WithSafeOrFreshCorrelationId() {
    final ApiExceptionHandler handler = new ApiExceptionHandler();

    final ResponseEntity<ErrorEnvelope> supplied =
        handler.handleUnexpected(new IllegalStateException("secret"), "abc-123");
    final ResponseEntity<ErrorEnvelope> unsafe =
        handler.handleUnexpected(new IllegalStateException("x"), "bad id\n");
    final ResponseEntity<ErrorEnvelope> absent =
        handler.handleUnexpected(new IllegalStateException("x"), null);

    assertEquals(500, supplied.getStatusCode().value());
    assertEquals("abc-123", supplied.getBody().correlationId());
    assertEquals("An unexpected error occurred", supplied.getBody().message());
    assertTrue(unsafe.getBody().correlationId().length() > 20);
    assertTrue(absent.getBody().correlationId().length() > 20);
  }
}
