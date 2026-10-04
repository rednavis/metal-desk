package com.rednavis.metaldesk.payments.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.payments.http.LogSafe;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** The refusals of the provider value types that the main tests leave out. */
class ProviderValidationGapsTest {

  private static final URI GOOD = URI.create("https://shop.example/return");
  private static final Money AMOUNT = Money.of("10.00", Currency.EUR);

  private static String codeOf(Executable action) {
    return assertThrows(ValidationException.class, action).code();
  }

  private static PaymentIntent intent(OrderId order, Money amount, PaymentMethod method) {
    return new PaymentIntent(
        order, amount, method, new CustomerId("c-1"), GOOD, GOOD, Locale.ENGLISH);
  }

  @Test
  void capabilityNeedsAnIdAndMethods() {
    assertEquals(
        "provider-capability.id-blank",
        codeOf(() -> new ProviderCapability(null, Set.of(PaymentMethod.CARD))));
    assertEquals(
        "provider-capability.id-blank",
        codeOf(() -> new ProviderCapability(" ", Set.of(PaymentMethod.CARD))));
    assertEquals(
        "provider-capability.methods-empty", codeOf(() -> new ProviderCapability("p", Set.of())));
    assertEquals(
        "provider-capability.methods-empty", codeOf(() -> new ProviderCapability("p", null)));
  }

  @Test
  void intentNeedsOrderMethodCustomerAndPositiveAmount() {
    assertEquals(
        "payment-intent.field-missing", codeOf(() -> intent(null, AMOUNT, PaymentMethod.CARD)));
    assertEquals(
        "payment-intent.field-missing", codeOf(() -> intent(new OrderId("o"), AMOUNT, null)));
    assertEquals(
        "payment-intent.amount-invalid",
        codeOf(() -> intent(new OrderId("o"), null, PaymentMethod.CARD)));
    assertEquals(
        "payment-intent.amount-invalid",
        codeOf(() -> intent(new OrderId("o"), Money.of("0.00", Currency.EUR), PaymentMethod.CARD)));
  }

  private static String uriCode(String bad) {
    return codeOf(
        () ->
            new PaymentIntent(
                new OrderId("o"),
                AMOUNT,
                PaymentMethod.CARD,
                new CustomerId("c"),
                URI.create(bad),
                GOOD,
                Locale.ENGLISH));
  }

  @Test
  void intentRefusesUrisThatAreNotAbsoluteWebAddresses() {
    assertEquals("payment-intent.uri-invalid", uriCode("ftp://x.example/a"));
    assertEquals("payment-intent.uri-invalid", uriCode("/relative"));
    assertEquals("payment-intent.uri-invalid", uriCode("https:///nohost"));
  }

  @Test
  void outcomesRefuseMissingPartsAndLongMessages() {
    final ProviderReference reference = new ProviderReference("r");
    assertEquals(
        "payment-outcome.handle-blank",
        codeOf(() -> new PaymentOutcome.ElementRequired(reference, " ")));
    assertEquals(
        "payment-outcome.handle-blank",
        codeOf(() -> new PaymentOutcome.ElementRequired(reference, null)));
    assertEquals(
        "payment-outcome.reference-missing",
        codeOf(() -> new PaymentOutcome.ElementRequired(null, "h")));
    assertEquals(
        "payment-outcome.decline-missing",
        codeOf(() -> new PaymentOutcome.Declined(null, Optional.empty())));
    assertEquals(
        "payment-outcome.decline-missing",
        codeOf(() -> new PaymentOutcome.Declined(DeclineReason.RISK_BLOCKED, null)));
    assertEquals(
        "payment-outcome.message-too-long",
        codeOf(
            () ->
                new PaymentOutcome.Declined(
                    DeclineReason.RISK_BLOCKED, Optional.of("x".repeat(5000)))));
    assertEquals(
        "payment-outcome.message-blank",
        codeOf(() -> new PaymentOutcome.Declined(DeclineReason.RISK_BLOCKED, Optional.of(" "))));
  }

  @Test
  void pendingOutcomesMapToPendingStatus() {
    final ProviderReference reference = new ProviderReference("r");
    assertEquals(
        com.rednavis.metaldesk.share.domain.payment.PaymentStatus.PENDING,
        new PaymentOutcome.ElementRequired(reference, "h").status());
    assertEquals(
        com.rednavis.metaldesk.share.domain.payment.PaymentStatus.PENDING,
        new PaymentOutcome.DocumentIssued(reference).status());
  }

  @Test
  void logSafeShowsNullAndCleansAndShortensOutsideValues() {
    assertEquals("(none)", LogSafe.code(null));
    assertEquals("a?b?c", LogSafe.code("a b\nc"));
    assertTrue(LogSafe.code("x".repeat(500)).length() < 500);
  }
}
