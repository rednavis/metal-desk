package com.rednavis.metaldesk.share.domain.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentRecordTest {

  private static final String PROVIDER = "mock-gateway";
  private static final ProviderReference REFERENCE = new ProviderReference("ch_test_0001");
  private static final Money AMOUNT = Money.of("4261.94", Currency.EUR);

  @Test
  void holdsOnlyTheFiveNamedComponents() {
    final Set<String> names =
        Arrays.stream(PaymentRecord.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
    assertEquals(Set.of("providerId", "method", "status", "reference", "amount"), names);
  }

  @Test
  void hasNoMapComponent() {
    assertFalse(
        Arrays.stream(PaymentRecord.class.getRecordComponents())
            .anyMatch(component -> Map.class.isAssignableFrom(component.getType())));
  }

  @Test
  void hasNoGenericBagOrInstrumentShapedComponentName() {
    final Set<String> bagNames = Set.of("metadata", "details", "raw", "response");
    final Pattern instrument = Pattern.compile("(?i)card|pan|cvv|iban|expiry");
    for (final RecordComponent component : PaymentRecord.class.getRecordComponents()) {
      assertFalse(
          bagNames.contains(component.getName().toLowerCase(Locale.ROOT)), component.getName());
      assertFalse(instrument.matcher(component.getName()).find(), component.getName());
    }
  }

  @Test
  void providerIdIsTrimmed() {
    assertEquals(
        PROVIDER,
        new PaymentRecord(
                "  mock-gateway ", PaymentMethod.CARD, PaymentStatus.CAPTURED, REFERENCE, AMOUNT)
            .providerId());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void blankProviderIdIsRefused(String providerId) {
    assertEquals(
        "payment-record.provider-blank",
        assertThrows(
                ValidationException.class,
                () ->
                    new PaymentRecord(
                        providerId, PaymentMethod.CARD, PaymentStatus.CAPTURED, REFERENCE, AMOUNT))
            .code());
  }

  @Test
  void amountMustBeGreaterThanZero() {
    final Money zero = Money.zero(Currency.EUR);
    assertEquals(
        "payment-record.amount-invalid",
        assertThrows(
                ValidationException.class,
                () ->
                    new PaymentRecord(
                        PROVIDER, PaymentMethod.CARD, PaymentStatus.CAPTURED, REFERENCE, zero))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    assertEquals(
        "payment-record.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new PaymentRecord(PROVIDER, null, PaymentStatus.CAPTURED, REFERENCE, AMOUNT))
            .code());
    assertEquals(
        "payment-record.field-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    new PaymentRecord(
                        PROVIDER, PaymentMethod.CARD, PaymentStatus.CAPTURED, null, AMOUNT))
            .code());
  }
}
