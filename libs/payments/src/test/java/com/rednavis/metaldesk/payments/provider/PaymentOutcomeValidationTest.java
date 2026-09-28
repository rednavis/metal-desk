package com.rednavis.metaldesk.payments.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentOutcomeValidationTest {

  private static final ProviderReference REFERENCE = StubProvider.reference();

  @Test
  void otherDeclineRequiresMessage() {
    assertEquals(
        "payment-outcome.decline-message-required",
        assertThrows(
                ValidationException.class,
                () -> new PaymentOutcome.Declined(DeclineReason.OTHER, Optional.empty()))
            .code());
  }

  @Test
  void otherDeclineWithMessageIsTrimmed() {
    final PaymentOutcome.Declined declined =
        new PaymentOutcome.Declined(DeclineReason.OTHER, Optional.of("  Try another method "));
    assertEquals(Optional.of("Try another method"), declined.message());
  }

  @Test
  void otherReasonsNeedNoMessage() {
    assertEquals(
        Optional.empty(),
        new PaymentOutcome.Declined(DeclineReason.RISK_BLOCKED, Optional.empty()).message());
  }

  @Test
  void blankOrOverlongMessagesAreRefused() {
    final String tooLong = "x".repeat(Checks.MAX_MESSAGE_LENGTH + 1);
    assertEquals(
        "payment-outcome.message-blank",
        assertThrows(ValidationException.class, () -> new PaymentOutcome.Failed(" ")).code());
    assertEquals(
        "payment-outcome.message-too-long",
        assertThrows(ValidationException.class, () -> new PaymentOutcome.Failed(tooLong)).code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/relative/path", "ftp://pay.example/x", "javascript:alert(1)", "https:///nohost"})
  void redirectMustBeAnAbsoluteWebUri(String uri) {
    final URI candidate = URI.create(uri);
    assertEquals(
        "payment-outcome.redirect-invalid",
        assertThrows(
                ValidationException.class,
                () -> new PaymentOutcome.RedirectRequired(REFERENCE, candidate))
            .code());
  }

  @Test
  void nullsAreRefused() {
    assertEquals(
        "payment-outcome.reference-missing",
        assertThrows(ValidationException.class, () -> new PaymentOutcome.Captured(null)).code());
    assertEquals(
        "payment-outcome.reference-missing",
        assertThrows(ValidationException.class, () -> new PaymentOutcome.DocumentIssued(null))
            .code());
    assertEquals(
        "payment-outcome.secret-blank",
        assertThrows(
                ValidationException.class, () -> new PaymentOutcome.ElementRequired(REFERENCE, " "))
            .code());
    assertEquals(
        "payment-outcome.decline-missing",
        assertThrows(
                ValidationException.class,
                () -> new PaymentOutcome.Declined(null, Optional.empty()))
            .code());
  }
}
