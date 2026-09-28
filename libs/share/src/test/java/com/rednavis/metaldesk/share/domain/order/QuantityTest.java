package com.rednavis.metaldesk.share.domain.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityTest {

  @Test
  void capIsTheFr33Figure() {
    assertEquals(10, Quantity.MAX);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 5, 10})
  void oneAndTheCapAreAccepted(int value) {
    assertEquals(value, Quantity.of(value).value());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void zeroAndNegativeAreRefused(int value) {
    assertEquals(
        "quantity.not-positive",
        assertThrows(ValidationException.class, () -> Quantity.of(value)).code());
  }

  @Test
  void aboveTheCapIsRefused() {
    assertEquals(
        "quantity.above-cap",
        assertThrows(ValidationException.class, () -> Quantity.of(Quantity.MAX + 1)).code());
  }

  @Test
  void factorIsExact() {
    assertEquals(BigDecimal.valueOf(3), Quantity.of(3).asFactor());
  }
}
