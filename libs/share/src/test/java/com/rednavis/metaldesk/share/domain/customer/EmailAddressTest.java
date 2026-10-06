package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailAddressTest {

  @Test
  void valueIsTrimmedAndLowerCased() {
    assertEquals("ann@example.com", new EmailAddress("  Ann@Example.COM ").value());
    assertEquals(new EmailAddress("ANN@example.com"), new EmailAddress("ann@EXAMPLE.com"));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankAddressIsRefused(String value) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new EmailAddress(value));
    assertEquals("email.blank", failure.code());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"ann", "ann@", "@example.com", "ann@example", "ann@@example.com", "a nn@x.com"})
  void malformedAddressIsRefused(String value) {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new EmailAddress(value));
    assertEquals("email.malformed", failure.code());
  }

  @Test
  void overlongAddressIsRefused() {
    final String tooLong = "a".repeat(250) + "@x.com";
    assertEquals(
        "email.malformed",
        assertThrows(ValidationException.class, () -> new EmailAddress(tooLong)).code());
  }

  @Test
  void failureMessageDoesNotEchoTheAddress() {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> new EmailAddress("secret-person"));
    assertFalse(failure.getMessage().contains("secret-person"));
  }
}
