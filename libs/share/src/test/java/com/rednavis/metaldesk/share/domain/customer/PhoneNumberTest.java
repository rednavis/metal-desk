package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

  @Test
  void formattingIsStripped() {
    assertEquals("+491701234567", new PhoneNumber(" +49 (170) 123-4567 ").value());
    assertEquals("+491701234567", new PhoneNumber("+49.170.123.4567").value());
  }

  @Test
  void numberWithoutPlusKeepsNoPlus() {
    assertEquals("01701234567", new PhoneNumber("0170 123 4567").value());
  }

  @Test
  void differentFormattingsOfOneNumberAreEqual() {
    assertEquals(new PhoneNumber("+49 170 1234567"), new PhoneNumber("+49-170-1234567"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankNumberIsRefused(String value) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new PhoneNumber(value));
    assertEquals("phone.blank", failure.code());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "+49 170 CALL-ME", "123456", "+1234567890123456", "12+3456789"})
  void malformedNumberIsRefused(String value) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new PhoneNumber(value));
    assertEquals("phone.malformed", failure.code());
  }
}
