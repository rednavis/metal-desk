package com.rednavis.metaldesk.api.checkout.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.payments.provider.PaymentIntent;
import com.rednavis.metaldesk.payments.provider.PaymentOutcome;
import com.rednavis.metaldesk.payments.provider.PaymentProvider;
import com.rednavis.metaldesk.payments.provider.ProviderCapability;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** Providers are found by what they support, and two claiming one method is a startup failure. */
class ProviderRegistryTest {

  /** A provider that serves the given methods and answers nothing. */
  private static PaymentProvider serving(String id, PaymentMethod... methods) {
    final ProviderCapability capability = new ProviderCapability(id, Set.of(methods));
    return new PaymentProvider() {
      @Override
      public ProviderCapability capability() {
        return capability;
      }

      @Override
      public Mono<PaymentOutcome> authorise(PaymentIntent intent) {
        return Mono.empty();
      }

      @Override
      public Mono<PaymentOutcome> confirm(ProviderReference reference) {
        return Mono.empty();
      }
    };
  }

  @Test
  void methodIsResolvedToTheProviderThatSupportsIt() {
    final PaymentProvider cards = serving("cards", PaymentMethod.CARD, PaymentMethod.BANK_DEBIT);
    final PaymentProvider invoices = serving("invoices", PaymentMethod.INVOICE);
    final ProviderRegistry registry = new ProviderRegistry(List.of(cards, invoices));

    assertEquals(Optional.of(cards), registry.forMethod(PaymentMethod.CARD));
    assertEquals(Optional.of(invoices), registry.forMethod(PaymentMethod.INVOICE));
    assertEquals(Optional.empty(), registry.forMethod(PaymentMethod.WALLET_ACCOUNT));
    assertEquals(
        Set.of(PaymentMethod.CARD, PaymentMethod.BANK_DEBIT, PaymentMethod.INVOICE),
        registry.supportedMethods());
  }

  @Test
  void theOrderOfTheProvidersDoesNotMatter() {
    final PaymentProvider first = serving("first", PaymentMethod.CARD);
    final PaymentProvider second = serving("second", PaymentMethod.INVOICE);

    assertEquals(
        Optional.of(first),
        new ProviderRegistry(List.of(second, first)).forMethod(PaymentMethod.CARD));
  }

  @Test
  void twoProvidersClaimingOneMethodIsStartupFailure() {
    final List<PaymentProvider> ambiguous =
        List.of(serving("one", PaymentMethod.CARD), serving("two", PaymentMethod.CARD));

    final IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> new ProviderRegistry(ambiguous));

    assertTrue(failure.getMessage().contains("CARD"));
  }
}
