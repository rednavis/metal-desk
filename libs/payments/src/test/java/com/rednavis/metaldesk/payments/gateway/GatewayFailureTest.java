package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProviderException;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import reactor.test.StepVerifier;

/** The transport paths: the gateway cannot be reached or understood, which is never a decline. */
class GatewayFailureTest {

  private static final Duration WAIT = Duration.ofSeconds(5);
  private static final String AUTHORISE_PATH = "/v1/payments";

  @RegisterExtension static final WireMockExtension WIRE_MOCK = GatewayFixtures.server();

  private static void expectTransportFailure(
      reactor.core.publisher.Mono<PaymentOutcome> call, PaymentProviderException.Kind kind) {
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
        GatewayFixtures.provider(WIRE_MOCK, 1).authorise(GatewayFixtures.intent("order-timeout")),
        PaymentProviderException.Kind.TIMEOUT);
  }

  @Test
  void timedOutAuthorisationIsNeverRetried() {
    WIRE_MOCK.resetRequests();
    expectTransportFailure(
        GatewayFixtures.provider(WIRE_MOCK, 3).authorise(GatewayFixtures.intent("order-timeout")),
        PaymentProviderException.Kind.TIMEOUT);
    WIRE_MOCK.verify(1, WireMock.postRequestedFor(WireMock.urlEqualTo(AUTHORISE_PATH)));
  }

  @Test
  void confirmationIsRetriedAfterTimeoutAndThenSucceeds() {
    WIRE_MOCK.resetRequests();
    WIRE_MOCK.resetScenarios();
    final ProviderReference reference = GatewayFixtures.reference("0004");
    StepVerifier.create(GatewayFixtures.provider(WIRE_MOCK, 1).confirm(reference))
        .expectNext(new PaymentOutcome.Captured(reference))
        .expectComplete()
        .verify(WAIT);
    WIRE_MOCK.verify(
        2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/payments/gw_test_0004/confirm")));
  }

  @Test
  void confirmationGivesUpAfterItsRetryBudget() {
    WIRE_MOCK.resetRequests();
    expectTransportFailure(
        GatewayFixtures.provider(WIRE_MOCK, 1).confirm(GatewayFixtures.reference("0005")),
        PaymentProviderException.Kind.TIMEOUT);
    WIRE_MOCK.verify(
        2, WireMock.postRequestedFor(WireMock.urlEqualTo("/v1/payments/gw_test_0005/confirm")));
  }

  @Test
  void refusedConnectionIsUnreachable() throws IOException {
    final URI running = URI.create(WIRE_MOCK.baseUrl());
    final int closedPort;
    try (ServerSocket socket = new ServerSocket(0)) {
      closedPort = socket.getLocalPort();
    }
    final GatewayConfiguration configuration =
        new GatewayConfiguration(
            URI.create("http://" + running.getHost() + ":" + closedPort),
            GatewayFixtures.SHORT_TIMEOUT,
            0);
    expectTransportFailure(
        new GatewayProvider(configuration).authorise(GatewayFixtures.intent("order-captured")),
        PaymentProviderException.Kind.UNREACHABLE);
  }

  @Test
  void serverErrorIsUnreachableAndItsBodyIsNotPassedOn() {
    StepVerifier.create(
            GatewayFixtures.provider(WIRE_MOCK, 1)
                .authorise(GatewayFixtures.intent("order-server-error")))
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
        GatewayFixtures.provider(WIRE_MOCK, 1).authorise(GatewayFixtures.intent("order-malformed")),
        PaymentProviderException.Kind.MALFORMED_RESPONSE);
  }

  @Test
  void unknownStatusOrMissingReferenceIsMalformed() {
    expectTransportFailure(
        GatewayFixtures.provider(WIRE_MOCK, 1).authorise(GatewayFixtures.intent("order-mystery")),
        PaymentProviderException.Kind.MALFORMED_RESPONSE);
    expectTransportFailure(
        GatewayFixtures.provider(WIRE_MOCK, 1)
            .authorise(GatewayFixtures.intent("order-no-reference")),
        PaymentProviderException.Kind.MALFORMED_RESPONSE);
  }
}
