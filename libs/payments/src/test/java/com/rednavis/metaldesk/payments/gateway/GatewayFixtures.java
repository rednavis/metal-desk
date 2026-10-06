package com.rednavis.metaldesk.payments.gateway;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/** Shared set-up for the gateway tests: a WireMock server and synthetic intents. */
public final class GatewayFixtures {

  /** Far shorter than the stubs' fixed delay, so the client's own timeout is what fires. */
  public static final Duration SHORT_TIMEOUT = Duration.ofMillis(400);

  private GatewayFixtures() {}

  /**
   * Builds a WireMock extension serving the gateway stubs from the test classpath.
   *
   * @return the extension, to be registered as a static field
   */
  public static WireMockExtension server() {
    return WireMockExtension.newInstance()
        .options(
            WireMockConfiguration.wireMockConfig()
                .dynamicPort()
                .usingFilesUnderClasspath("wiremock/gateway"))
        .build();
  }

  /**
   * Builds a provider pointed at a WireMock server.
   *
   * @param wireMock the server
   * @param retryBudget how many extra attempts a confirm call may make
   * @return the provider, with {@link #SHORT_TIMEOUT}
   */
  public static GatewayProvider provider(WireMockExtension wireMock, int retryBudget) {
    return new GatewayProvider(
        new GatewayConfiguration(URI.create(wireMock.baseUrl()), SHORT_TIMEOUT, retryBudget));
  }

  /**
   * Builds a synthetic card payment intent for an order id, which is what the stubs match on.
   *
   * @param orderId the order id, such as {@code order-captured}
   * @return the intent
   */
  public static PaymentIntent intent(String orderId) {
    return intent(orderId, PaymentMethod.CARD);
  }

  /**
   * Builds a synthetic payment intent for an order id and method.
   *
   * @param orderId the order id
   * @param method the payment method
   * @return the intent
   */
  public static PaymentIntent intent(String orderId, PaymentMethod method) {
    return new PaymentIntent(
        new OrderId(orderId),
        Money.of("12.30", Currency.EUR),
        method,
        new CustomerId("customer-1"),
        URI.create("https://shop.example/return"),
        URI.create("https://shop.example/cancel"),
        Locale.ENGLISH);
  }

  /**
   * Builds a gateway payment reference.
   *
   * @param suffix the part after {@code gw_test_}
   * @return the reference
   */
  public static ProviderReference reference(String suffix) {
    return new ProviderReference("gw_test_" + suffix);
  }
}
