package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

/** A stubbed gateway: every payment is captured and nothing leaves the process. */
class GatewayStubTest {

  @RegisterExtension static final WireMockExtension WIRE_MOCK = GatewayFixtures.server();

  private static GatewayProvider stubbed() {
    return new GatewayProvider(
        new GatewayConfiguration(
            URI.create(WIRE_MOCK.baseUrl()), GatewayFixtures.SHORT_TIMEOUT, 1, true));
  }

  @Test
  void authoriseIsCapturedWithMadeUpReferenceAndMakesNoRequest() {
    StepVerifier.create(stubbed().authorise(GatewayFixtures.intent("order-declined")))
        .assertNext(
            outcome -> {
              final PaymentOutcome.Captured captured =
                  assertInstanceOf(PaymentOutcome.Captured.class, outcome);
              assertTrue(captured.reference().value().startsWith("stub-"));
            })
        .verifyComplete();

    assertTrue(WIRE_MOCK.getAllServeEvents().isEmpty(), "a stub must not call the provider");
  }

  @Test
  void confirmIsCapturedAndMakesNoRequest() {
    StepVerifier.create(stubbed().confirm(GatewayFixtures.reference("0001")))
        .assertNext(outcome -> assertInstanceOf(PaymentOutcome.Captured.class, outcome))
        .verifyComplete();

    assertTrue(WIRE_MOCK.getAllServeEvents().isEmpty(), "a stub must not call the provider");
  }
}
