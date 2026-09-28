package com.rednavis.metaldesk.payments.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PaymentIntentTest {

  private static final OrderId ORDER = new OrderId("o-1");
  private static final Money AMOUNT = Money.of("100.00", Currency.EUR);
  private static final CustomerId CUSTOMER = new CustomerId("c-1");
  private static final URI GOOD = URI.create("https://shop.example/return");

  @Test
  void holdsReferencesAndAnAmountOnly() {
    final PaymentIntent intent = StubProvider.intent();
    assertEquals(PaymentMethod.CARD, intent.method());
    assertEquals(Money.of("4261.94", Currency.EUR), intent.amount());
  }

  @Test
  void hasNoMapComponent() {
    assertFalse(
        Arrays.stream(PaymentIntent.class.getRecordComponents())
            .anyMatch(component -> Map.class.isAssignableFrom(component.getType())));
  }

  @Test
  void hasNoInstrumentShapedComponentName() {
    final Pattern instrument = Pattern.compile("(?i)card|pan|cvv|iban|expiry|token");
    for (final RecordComponent component : PaymentIntent.class.getRecordComponents()) {
      assertFalse(instrument.matcher(component.getName()).find(), component.getName());
    }
  }

  @Test
  void amountMustBeGreaterThanZero() {
    final Money zero = Money.zero(Currency.EUR);
    assertEquals(
        "payment-intent.amount-invalid",
        assertThrows(
                ValidationException.class,
                () -> new PaymentIntent(ORDER, zero, PaymentMethod.CARD, CUSTOMER, GOOD, GOOD))
            .code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/relative", "ftp://shop.example/x", "javascript:alert(1)", "file:///etc"})
  void targetsMustBeAbsoluteWebUris(String uri) {
    final URI bad = URI.create(uri);
    assertEquals(
        "payment-intent.uri-invalid",
        assertThrows(
                ValidationException.class,
                () -> new PaymentIntent(ORDER, AMOUNT, PaymentMethod.CARD, CUSTOMER, bad, GOOD))
            .code());
    assertEquals(
        "payment-intent.uri-invalid",
        assertThrows(
                ValidationException.class,
                () -> new PaymentIntent(ORDER, AMOUNT, PaymentMethod.CARD, CUSTOMER, GOOD, bad))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    assertEquals(
        "payment-intent.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new PaymentIntent(null, AMOUNT, PaymentMethod.CARD, CUSTOMER, GOOD, GOOD))
            .code());
    assertEquals(
        "payment-intent.uri-invalid",
        assertThrows(
                ValidationException.class,
                () -> new PaymentIntent(ORDER, AMOUNT, PaymentMethod.CARD, CUSTOMER, null, GOOD))
            .code());
  }
}
