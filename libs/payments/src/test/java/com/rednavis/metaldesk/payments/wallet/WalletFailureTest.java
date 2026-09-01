package com.rednavis.metaldesk.payments.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** The transport paths of the wallet: not reached or not understood, which is never a decline. */
class WalletFailureTest {

  private static final Duration WAIT = Duration.ofSeconds(5);

  @RegisterExtension static final WireMockExtension WIRE_MOCK = WalletFixtures.server();

  private static void expectTransportFailure(
      Mono<PaymentOutcome> call, PaymentProviderException.Kind kind) {
    StepVerifier.create(call)
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof PaymentProviderException, error.toString());
              assertEquals(kind, ((PaymentProviderException) error).kind());
            })
        .verify(WAIT);
  }

  @Test
  void delayedAnswerIsProviderExceptionNotDecline() {
    // The stub delays far longer than the configured timeout, so the client's own timeout fires.
    // expectErrorSatisfies also fails the test if any outcome, a Declined included, is emitted.
    expectTransportFailure(
        WalletFixtures.provider(WIRE_MOCK, 1).authorise(WalletFixtures.intent("order-timeout")),
        PaymentProviderException.Kind.TIMEOUT);
  }

  @Test
  void timedOutAuthorisationIsNeverRetried() {
    WIRE_MOCK.resetRequests();
    expectTransportFailure(
        WalletFixtures.provider(WIRE_MOCK, 3).authorise(WalletFixtures.intent("order-timeout")),
        PaymentProviderException.Kind.TIMEOUT);
    WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/wallet/payments")));
  }

  @Test
  void serverErrorIsUnreachableAndItsBodyIsNotPassedOn() {
    StepVerifier.create(
            WalletFixtures.provider(WIRE_MOCK, 1)
                .authorise(WalletFixtures.intent("order-server-error")))
        .expectErrorSatisfies(
            error -> {
              assertEquals(
                  PaymentProviderException.Kind.UNREACHABLE,
                  ((PaymentProviderException) error).kind());
              assertFalse(error.getMessage().contains("upstream broke"));
            })
        .verify(WAIT);
  }

  @Test
  void unparseableBodyIsMalformed() {
    expectTransportFailure(
        WalletFixtures.provider(WIRE_MOCK, 1).authorise(WalletFixtures.intent("order-malformed")),
        PaymentProviderException.Kind.MALFORMED_RESPONSE);
  }
}
