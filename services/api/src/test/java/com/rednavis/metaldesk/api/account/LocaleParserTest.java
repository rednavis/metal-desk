package com.rednavis.metaldesk.api.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** A language tag is read as given, or defaulted when absent; nonsense is refused. */
class LocaleParserTest {

  @Test
  void absentOrBlankTagGivesTheDefault() {
    assertEquals(LocaleParser.parse(null), LocaleParser.parse("  "));
  }

  @Test
  void tagIsReadAndStripped() {
    assertEquals(Locale.GERMAN, LocaleParser.parse(" de "));
  }

  @Test
  void tagWithoutLanguageIsRefused() {
    assertEquals(
        "locale.invalid",
        assertThrows(ValidationException.class, () -> LocaleParser.parse("---")).code());
  }

  @Test
  void overlongTagIsRefused() {
    final String tag = "x".repeat(200);
    assertThrows(ValidationException.class, () -> LocaleParser.parse(tag));
  }
}
