package com.rednavis.metaldesk.share.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegionTest {

  @Test
  void codeIsTrimmedAndUpperCased() {
    assertEquals("EU-CORE", new Region("  eu-core ").code());
    assertEquals(new Region("de"), new Region("DE"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankCodeIsRefused(String code) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new Region(code));
    assertEquals("region.blank", failure.code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"-EU", "EU CORE", "EU/CORE", "EU.CORE", "A234567890123456789012345678901234"})
  void malformedCodeIsRefused(String code) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new Region(code));
    assertEquals("region.malformed", failure.code());
  }
}
