package com.rednavis.metaldesk.payments.wallet;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

/** A stubbed wallet: every payment is captured and nothing leaves the process. */
class WalletStubTest {

  @RegisterExtension static final WireMockExtension WIRE_MOCK = WalletFixtures.server();

  private static WalletProvider stubbed() {
    return new WalletProvider(
        new WalletConfiguration(
            URI.create(WIRE_MOCK.baseUrl()), WalletFixtures.SHORT_TIMEOUT, 1, true));
  }

  @Test
  void authoriseIsCapturedWithMadeUpReferenceAndMakesNoRequest() {
    StepVerifier.create(stubbed().authorise(WalletFixtures.intent("order-declined")))
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
    StepVerifier.create(stubbed().confirm(WalletFixtures.reference("0001")))
        .assertNext(outcome -> assertInstanceOf(PaymentOutcome.Captured.class, outcome))
        .verifyComplete();

    assertTrue(WIRE_MOCK.getAllServeEvents().isEmpty(), "a stub must not call the provider");
  }
}
