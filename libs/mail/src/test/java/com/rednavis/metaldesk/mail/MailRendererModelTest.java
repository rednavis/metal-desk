package com.rednavis.metaldesk.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** How the renderer treats the model it is given. */
class MailRendererModelTest {

  private final MailRenderer renderer = new MailRenderer();

  private static Map<String, Object> model() {
    return new HashMap<>(MailFixtures.universalModel());
  }

  @Test
  void missingModelValueIsRefusedNamingTheKey() {
    final Map<String, Object> model = model();
    model.remove("code");
    final ValidationException thrown =
        assertThrows(
            ValidationException.class,
            () -> renderer.render(MailTemplate.EMAIL_VERIFICATION, model, Locale.ENGLISH));
    assertEquals("mail-renderer.model-missing", thrown.code());
    assertTrue(thrown.getMessage().contains("code"));
  }

  @Test
  void nullModelValueCountsAsMissing() {
    final Map<String, Object> model = model();
    model.put("name", null);
    assertEquals(
        "mail-renderer.model-missing",
        assertThrows(
                ValidationException.class,
                () -> renderer.render(MailTemplate.EMAIL_VERIFICATION, model, Locale.ENGLISH))
            .code());
  }

  @Test
  void moneyIsFormattedForTheLocaleThatIsUsed() {
    final Map<String, Object> model = model();
    final String english =
        renderer.render(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, model, Locale.ENGLISH).body();
    final String german =
        renderer.render(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, model, Locale.GERMAN).body();
    assertTrue(english.contains("1,959.32"));
    assertTrue(german.contains("1.959,32"));
  }

  @Test
  void subjectIsReducedToOneLineSoValueCannotInjectHeader() {
    final Map<String, Object> model = model();
    model.put("invoiceNumber", "INV-1\r\nBcc: someone@example.com");
    final RenderedMail rendered =
        renderer.render(MailTemplate.INVOICE_CUSTOMER, model, Locale.ENGLISH);
    assertFalse(rendered.subject().contains("\n"));
    assertFalse(rendered.subject().contains("\r"));
    assertTrue(rendered.subject().startsWith("Your invoice INV-1"));
  }

  @Test
  void valuesAreInsertedVerbatimEvenWithRegexCharacters() {
    final Map<String, Object> model = model();
    model.put("name", "Ann $1 \\ {{code}}");
    final String body =
        renderer.render(MailTemplate.EMAIL_VERIFICATION, model, Locale.ENGLISH).body();
    assertTrue(body.contains("Hello Ann $1 \\ {{code}},"));
  }

  @Test
  void extraModelEntriesAreIgnored() {
    final Map<String, Object> model = model();
    model.put("unusedEntry", "ignored");
    assertEquals(
        renderer.render(
            MailTemplate.EMAIL_VERIFICATION, MailFixtures.universalModel(), Locale.ENGLISH),
        renderer.render(MailTemplate.EMAIL_VERIFICATION, model, Locale.ENGLISH));
  }

  @Test
  void nullArgumentsAreRefused() {
    final Map<String, Object> model = model();
    assertEquals(
        "mail-renderer.input-missing",
        assertThrows(ValidationException.class, () -> renderer.render(null, model, Locale.ENGLISH))
            .code());
    assertEquals(
        "mail-renderer.input-missing",
        assertThrows(
                ValidationException.class,
                () -> renderer.render(MailTemplate.PASSWORD_RESET, null, Locale.ENGLISH))
            .code());
    assertEquals(
        "mail-renderer.input-missing",
        assertThrows(
                ValidationException.class,
                () -> renderer.render(MailTemplate.PASSWORD_RESET, model, null))
            .code());
  }
}
