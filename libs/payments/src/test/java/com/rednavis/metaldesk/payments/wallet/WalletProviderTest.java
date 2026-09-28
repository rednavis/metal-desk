package com.rednavis.metaldesk.payments.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.rednavis.metaldesk.payments.provider.DeclineReason;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import reactor.test.StepVerifier;

/** The success and decline paths of the wallet, answered by WireMock. */
class WalletProviderTest {

  @RegisterExtension static final WireMockExtension WIRE_MOCK = WalletFixtures.server();

  private static WalletProvider provider() {
    return WalletFixtures.provider(WIRE_MOCK, 1);
  }

  @Test
  void servesOnlyTheWalletMethod() {
    final WalletProvider provider = provider();
    final Set<PaymentMethod> served =
        Arrays.stream(PaymentMethod.values())
            .filter(provider::supports)
            .collect(Collectors.toSet());
    assertEquals(Set.of(PaymentMethod.WALLET_ACCOUNT), served);
  }

  @Test
  void capabilityDoesNotOverlapTheGatewaysOrTheInvoices() {
    final Set<PaymentMethod> wallet = provider().capability().methods();
    assertFalse(wallet.contains(PaymentMethod.SAVED_WALLET));
    assertFalse(wallet.contains(PaymentMethod.CARD));
    assertFalse(wallet.contains(PaymentMethod.INVOICE));
    assertEquals("wallet", provider().capability().providerId());
  }

  @Test
  void captureIsCapturedWithTheProvidersReference() {
    StepVerifier.create(provider().authorise(WalletFixtures.intent("order-captured")))
        .expectNext(new PaymentOutcome.Captured(WalletFixtures.reference("0001")))
        .verifyComplete();
  }

  @ParameterizedTest
  @CsvSource({
    "insufficient_balance,INSUFFICIENT_FUNDS",
    "account_blocked,RISK_BLOCKED",
    "account_closed,INSTRUMENT_REJECTED"
  })
  void eachMappedDeclineCodeIsDeclinedWithItsReason(String code, DeclineReason reason) {
    StepVerifier.create(provider().authorise(WalletFixtures.intent("order-declined-" + code)))
        .expectNext(new PaymentOutcome.Declined(reason, Optional.empty()))
        .verifyComplete();
  }

  @Test
  void insufficientBalanceIsInsufficientFundsSpecifically() {
    StepVerifier.create(
            provider().authorise(WalletFixtures.intent("order-declined-insufficient_balance")))
        .expectNextMatches(
            outcome ->
                outcome instanceof PaymentOutcome.Declined declined
                    && declined.reason() == DeclineReason.INSUFFICIENT_FUNDS)
        .verifyComplete();
  }

  @Test
  void unmappedDeclineCodeIsOtherWithSafeMessageAndDoesNotThrow() {
    StepVerifier.create(provider().authorise(WalletFixtures.intent("order-declined-something_new")))
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
    StepVerifier.create(provider().authorise(WalletFixtures.intent("order-failed")))
        .expectNextMatches(
            outcome ->
                outcome instanceof PaymentOutcome.Failed failed
                    && failed.message().startsWith("The payment could not be completed"))
        .verifyComplete();
  }

  @Test
  void confirmReadsTheState() {
    StepVerifier.create(provider().confirm(WalletFixtures.reference("0002")))
        .expectNext(new PaymentOutcome.Captured(WalletFixtures.reference("0002")))
        .verifyComplete();
  }

  @Test
  void methodTheWalletDoesNotServeIsRefusedWithoutAnyRequest() {
    WIRE_MOCK.resetRequests();
    StepVerifier.create(
            provider().authorise(WalletFixtures.intent("order-captured", PaymentMethod.CARD)))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof ValidationException);
              assertEquals("wallet.method-unsupported", ((ValidationException) error).code());
            })
        .verify();
    assertEquals(0, WIRE_MOCK.getAllServeEvents().size());
  }
}
