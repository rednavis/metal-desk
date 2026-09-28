package com.rednavis.metaldesk.payments.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.domain.payment.ProviderReference;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class PaymentProviderContractTest {

  @Test
  void declaresOnlyCapabilitySupportsAuthoriseAndConfirm() {
    final Set<String> names =
        Arrays.stream(PaymentProvider.class.getDeclaredMethods())
            .filter(method -> !method.isSynthetic())
            .map(Method::getName)
            .collect(Collectors.toSet());
    assertEquals(Set.of("capability", "supports", "authorise", "confirm"), names);
  }

  @Test
  void signaturesMentionNoHttpTypeUrlStringOrVendorClass() {
    final Set<Class<?>> allowed =
        Set.of(
            ProviderCapability.class,
            PaymentMethod.class,
            PaymentIntent.class,
            ProviderReference.class,
            Mono.class,
            boolean.class);
    for (final Method method : PaymentProvider.class.getDeclaredMethods()) {
      if (method.isSynthetic()) {
        continue;
      }
      assertTrue(allowed.contains(method.getReturnType()), method.getName());
      assertTrue(allowed.containsAll(Arrays.asList(method.getParameterTypes())), method.getName());
    }
  }

  @Test
  void authoriseAndConfirmReturnReactiveType() throws NoSuchMethodException {
    assertEquals(
        Mono.class,
        PaymentProvider.class.getMethod("authorise", PaymentIntent.class).getReturnType());
    assertEquals(
        Mono.class,
        PaymentProvider.class.getMethod("confirm", ProviderReference.class).getReturnType());
  }

  @Test
  void supportsFollowsTheCapability() {
    final PaymentProvider provider = new StubProvider(Mono.empty());
    assertTrue(provider.supports(PaymentMethod.CARD));
    assertFalse(provider.supports(PaymentMethod.INVOICE));
  }

  @Test
  void capabilityCopiesItsMethodsAndRefusesNone() {
    final ProviderCapability capability =
        new ProviderCapability(" gateway ", Set.of(PaymentMethod.CARD, PaymentMethod.BANK_DEBIT));
    assertEquals("gateway", capability.providerId());
    assertTrue(capability.supports(PaymentMethod.BANK_DEBIT));
    assertEquals(
        "provider-capability.methods-empty",
        assertThrows(ValidationException.class, () -> new ProviderCapability("g", Set.of()))
            .code());
  }

  @Test
  void declineReasonsAreVendorNeutral() {
    final Set<String> names =
        Stream.of(DeclineReason.values()).map(Enum::name).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "INSUFFICIENT_FUNDS",
            "INSTRUMENT_REJECTED",
            "AUTHENTICATION_FAILED",
            "RISK_BLOCKED",
            "EXPIRED",
            "OTHER"),
        names);
    assertTrue(names.stream().noneMatch(name -> name.chars().anyMatch(Character::isDigit)));
  }
}
