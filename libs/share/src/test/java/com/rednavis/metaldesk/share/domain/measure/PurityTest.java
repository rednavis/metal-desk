package com.rednavis.metaldesk.share.domain.measure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PurityTest {

  @ParameterizedTest
  @ValueSource(strings = {"999.9", "1000", "916.7", "0.1"})
  void acceptsFinenessInsideTheRange(String fineness) {
    assertEquals(new BigDecimal(fineness), Purity.of(fineness).partsPerThousand());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1", "1000.1", "1e4"})
  void rejectsFinenessOutsideTheRange(String fineness) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Purity.of(fineness));
    assertEquals("purity.out-of-range", failure.code());
  }

  @Test
  void trailingZerosDoNotAffectEquality() {
    assertEquals(Purity.of("999.90"), Purity.of("999.9"));
  }

  @Test
  void ordersByFineness() {
    assertTrue(Purity.of("916.7").compareTo(Purity.of("999.9")) < 0);
  }

  @Test
  void nullAndMalformedTextAreRefused() {
    assertThrows(ValidationException.class, () -> new Purity(null));
    assertThrows(ValidationException.class, () -> Purity.of(null));
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Purity.of("pure"));
    assertEquals("purity.malformed", failure.code());
  }
}
