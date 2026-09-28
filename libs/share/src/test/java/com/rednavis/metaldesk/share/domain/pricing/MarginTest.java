package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarginTest {

  @Test
  void fractionIsExact() {
    assertEquals(new BigDecimal("0.05"), Margin.of("5").asFraction());
    assertEquals(new BigDecimal("0.025"), Margin.of("2.5").asFraction());
  }

  @Test
  void trailingZerosDoNotMatter() {
    assertEquals(Margin.of("5"), Margin.of("5.00"));
    assertEquals(Margin.of("0"), Margin.of("0.0"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "100"})
  void boundsAreAccepted(String percent) {
    assertEquals(new BigDecimal(percent), Margin.of(percent).percent());
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.01", "-5"})
  void negativeMarginIsRefused(String percent) {
    assertEquals(
        "margin.negative",
        assertThrows(ValidationException.class, () -> Margin.of(percent)).code());
  }

  @Test
  void marginAboveOneHundredIsRefused() {
    assertEquals(
        "margin.too-high",
        assertThrows(ValidationException.class, () -> Margin.of("100.01")).code());
  }

  @Test
  void malformedAndMissingAreRefused() {
    assertEquals(
        "margin.malformed", assertThrows(ValidationException.class, () -> Margin.of("5%")).code());
    assertEquals(
        "margin.required", assertThrows(ValidationException.class, () -> Margin.of(null)).code());
    assertEquals(
        "margin.required", assertThrows(ValidationException.class, () -> new Margin(null)).code());
  }
}
