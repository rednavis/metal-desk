package com.rednavis.metaldesk.payments.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class PaymentOutcomeTest {

  private static String describe(PaymentOutcome outcome) {
    return switch (outcome) {
      case PaymentOutcome.Captured _ -> "captured";
      case PaymentOutcome.RedirectRequired _ -> "redirect";
      case PaymentOutcome.ElementRequired _ -> "element";
      case PaymentOutcome.DocumentIssued _ -> "document";
      case PaymentOutcome.Declined _ -> "declined";
      case PaymentOutcome.Failed _ -> "failed";
    };
  }

  @Test
  void isSealedAndPermitsExactlyTheSixVariants() {
    assertTrue(PaymentOutcome.class.isSealed());
    final Set<String> permitted =
        Arrays.stream(PaymentOutcome.class.getPermittedSubclasses())
            .map(Class::getSimpleName)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "Captured",
            "RedirectRequired",
            "ElementRequired",
            "DocumentIssued",
            "Declined",
            "Failed"),
        permitted);
  }

  @Test
  void switchOverTheOutcomeIsExhaustive() {
    assertEquals("captured", describe(new PaymentOutcome.Captured(StubProvider.reference())));
    assertEquals(
        "declined", describe(new PaymentOutcome.Declined(DeclineReason.EXPIRED, Optional.empty())));
    assertEquals("failed", describe(new PaymentOutcome.Failed("Please try again")));
  }

  @Test
  void noOutcomeIsThrowableButTransportFailureIs() {
    for (final Class<?> variant : PaymentOutcome.class.getPermittedSubclasses()) {
      assertFalse(Throwable.class.isAssignableFrom(variant), variant.getSimpleName());
    }
    assertTrue(Throwable.class.isAssignableFrom(PaymentProviderException.class));
  }

  @Test
  void declineIsValueNotError() {
    final PaymentOutcome declined =
        new PaymentOutcome.Declined(DeclineReason.INSUFFICIENT_FUNDS, Optional.empty());
    StepVerifier.create(new StubProvider(Mono.just(declined)).authorise(StubProvider.intent()))
        .expectNext(declined)
        .verifyComplete();
  }

  @Test
  void transportFailureIsErrorOfItsOwnType() {
    final PaymentProviderException timeout =
        new PaymentProviderException(
            PaymentProviderException.Kind.TIMEOUT, "Provider did not answer", null);
    StepVerifier.create(new StubProvider(Mono.error(timeout)).authorise(StubProvider.intent()))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof PaymentProviderException);
              assertEquals(
                  PaymentProviderException.Kind.TIMEOUT, ((PaymentProviderException) error).kind());
            })
        .verify();
  }

  @Test
  void confirmAlsoAnswersWithAnOutcome() {
    final PaymentOutcome captured = new PaymentOutcome.Captured(StubProvider.reference());
    StepVerifier.create(new StubProvider(Mono.just(captured)).confirm(StubProvider.reference()))
        .expectNext(captured)
        .verifyComplete();
  }

  @Test
  void elementHandleDoesNotAppearInItsStringForm() {
    final PaymentOutcome element =
        new PaymentOutcome.ElementRequired(StubProvider.reference(), "hdl_test_abcdef");
    assertFalse(element.toString().contains("hdl_test_abcdef"));
    assertTrue(element.toString().contains("ch_test_0001"));
  }

  @Test
  void redirectKeepsItsUri() {
    final URI target = URI.create("https://pay.example/session/1");
    assertEquals(
        target,
        new PaymentOutcome.RedirectRequired(StubProvider.reference(), target).redirectUri());
  }
}
