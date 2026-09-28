package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

/** The success and decline paths, answered by WireMock. */
class GatewayProviderTest {

  @RegisterExtension static final WireMockExtension WIRE_MOCK = GatewayFixtures.server();

  private static GatewayProvider provider() {
    return GatewayFixtures.provider(WIRE_MOCK, 1);
  }

  @Test
  void servesExactlyTheFiveGatewayMethods() {
    final GatewayProvider provider = provider();
    final Set<PaymentMethod> served =
        Arrays.stream(PaymentMethod.values())
            .filter(provider::supports)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            PaymentMethod.CARD,
            PaymentMethod.BANK_DEBIT,
            PaymentMethod.BANK_REDIRECT,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.SAVED_WALLET),
        served);
    assertFalse(provider.supports(PaymentMethod.WALLET_ACCOUNT));
    assertFalse(provider.supports(PaymentMethod.INVOICE));
  }

  @Test
  void captureIsCapturedWithTheGatewaysReference() {
    StepVerifier.create(provider().authorise(GatewayFixtures.intent("order-captured")))
        .expectNext(new PaymentOutcome.Captured(GatewayFixtures.reference("0001")))
        .verifyComplete();
  }

  @Test
  void redirectIsRedirectRequiredWithItsTarget() {
    StepVerifier.create(provider().authorise(GatewayFixtures.intent("order-redirect")))
        .expectNext(
            new PaymentOutcome.RedirectRequired(
                GatewayFixtures.reference("0002"),
                URI.create("https://pay.example/session/gw_test_0002")))
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({
    "insufficient_funds,INSUFFICIENT_FUNDS",
    "instrument_refused,INSTRUMENT_REJECTED",
    "authentication_failed,AUTHENTICATION_FAILED",
    "risk_blocked,RISK_BLOCKED",
    "instrument_expired,EXPIRED"
  })
  void eachMappedDeclineCodeIsDeclinedWithItsReason(String code, DeclineReason reason) {
    StepVerifier.create(provider().authorise(GatewayFixtures.intent("order-declined-" + code)))
        .expectNext(new PaymentOutcome.Declined(reason, Optional.empty()))
        .verifyComplete();
  }

  @Test
  void unmappedDeclineCodeIsOtherWithSafeMessageAndDoesNotThrow() {
    StepVerifier.create(
            provider().authorise(GatewayFixtures.intent("order-declined-something_new")))
        .expectNextMatches(
            outcome ->
                outcome instanceof PaymentOutcome.Declined declined
                    && declined.reason() == DeclineReason.OTHER
                    && declined.message().isPresent()
                    && !declined.message().get().contains("something_new"))
        .verifyComplete();
  }

  @Test
  void failureIsFailedWithMessageWrittenHere() {
    StepVerifier.create(provider().authorise(GatewayFixtures.intent("order-failed")))
        .expectNextMatches(
            outcome ->
                outcome instanceof PaymentOutcome.Failed failed
                    && failed.message().startsWith("The payment could not be completed"))
        .verifyComplete();
  }

  @Test
  void confirmReturnsCapturedOrDeclined() {
    StepVerifier.create(provider().confirm(GatewayFixtures.reference("0002")))
        .expectNext(new PaymentOutcome.Captured(GatewayFixtures.reference("0002")))
        .verifyComplete();
    StepVerifier.create(provider().confirm(GatewayFixtures.reference("0003")))
        .expectNext(new PaymentOutcome.Declined(DeclineReason.INSUFFICIENT_FUNDS, Optional.empty()))
        .verifyComplete();
  }

  @Test
  void methodTheGatewayDoesNotServeIsRefusedWithoutAnyRequest() {
    WIRE_MOCK.resetRequests();
    StepVerifier.create(
            provider().authorise(GatewayFixtures.intent("order-captured", PaymentMethod.INVOICE)))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof ValidationException);
              assertEquals("gateway.method-unsupported", ((ValidationException) error).code());
            })
        .verify();
    assertEquals(0, WIRE_MOCK.getAllServeEvents().size());
  }
}
