package com.rednavis.metaldesk.share.domain.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProviderReferenceTest {

  @ParameterizedTest
  @ValueSource(strings = {"ch_3Nq8xYz", "INV-2026-000123", "tok/abc.def=", "1"})
  void opaqueHandlesAreAccepted(String value) {
    assertEquals(value, new ProviderReference(value).value());
  }

  @Test
  void surroundingWhitespaceIsTrimmed() {
    assertEquals("ch_1", new ProviderReference("  ch_1 ").value());
  }

  @Test
  void theLongestAllowedReferenceIsAccepted() {
    final String longest = "a".repeat(ProviderReference.MAX_LENGTH);
    assertEquals(longest, new ProviderReference(longest).value());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankIsRefused(String value) {
    assertEquals(
        "provider-reference.blank",
        assertThrows(ValidationException.class, () -> new ProviderReference(value)).code());
  }

  @ParameterizedTest
  @ValueSource(strings = {"two words", "with\ttab", "café", "a\nb"})
  void whitespaceAndNonAsciiAreRefused(String value) {
    assertEquals(
        "provider-reference.malformed",
        assertThrows(ValidationException.class, () -> new ProviderReference(value)).code());
  }

  @Test
  void referenceLongerThanTheLimitIsRefused() {
    final String tooLong = "a".repeat(ProviderReference.MAX_LENGTH + 1);
    assertEquals(
        "provider-reference.malformed",
        assertThrows(ValidationException.class, () -> new ProviderReference(tooLong)).code());
  }
}
