package com.rednavis.metaldesk.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rednavis.metaldesk.share.domain.payment.PaymentMethod;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GatewayRequestTest {

  @Test
  void wireFormCarriesAmountAsPlainDecimalString() {
    final GatewayRequest request =
        GatewayRequest.from(GatewayFixtures.intent("order-captured", PaymentMethod.BANK_REDIRECT));
    assertEquals("12.30", request.amount());
    assertEquals("EUR", request.currency());
    assertEquals("bank_redirect", request.method());
    assertEquals("order-captured", request.orderId());
  }

  @Test
  void requestHasNoMapAndNoInstrumentShapedComponent() {
    final Pattern instrument = Pattern.compile("(?i)card|pan|cvv|iban|expiry|token");
    for (final RecordComponent component : GatewayRequest.class.getRecordComponents()) {
      assertFalse(Map.class.isAssignableFrom(component.getType()), component.getName());
      assertFalse(instrument.matcher(component.getName()).find(), component.getName());
    }
    assertEquals(7, GatewayRequest.class.getRecordComponents().length);
    assertFalse(
        Arrays.stream(GatewayResponse.class.getRecordComponents())
            .anyMatch(component -> Map.class.isAssignableFrom(component.getType())));
  }
}
