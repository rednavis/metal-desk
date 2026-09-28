package com.rednavis.metaldesk.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** The template set and its localization. */
class MailRendererTest {

  private final MailRenderer renderer = new MailRenderer();

  private RenderedMail render(MailTemplate template, Locale locale) {
    return renderer.render(template, MailFixtures.universalModel(), locale);
  }

  @ParameterizedTest
  @EnumSource(MailTemplate.class)
  void everyTemplateHasDefaultLocaleRendering(MailTemplate template) {
    final RenderedMail rendered = render(template, MailRenderer.DEFAULT_LOCALE);
    assertFalse(rendered.subject().isBlank());
    assertFalse(rendered.body().isBlank());
    assertFalse(rendered.subject().contains("{{"), template.name());
    assertFalse(rendered.body().contains("{{"), template.name());
    assertEquals(Locale.ENGLISH, rendered.locale());
  }

  @ParameterizedTest
  @EnumSource(MailTemplate.class)
  void englishAndGermanDifferInSubjectAndInBody(MailTemplate template) {
    final RenderedMail english = render(template, Locale.ENGLISH);
    final RenderedMail german = render(template, Locale.GERMAN);
    assertNotEquals(english.subject(), german.subject(), "subject of " + template);
    assertNotEquals(english.body(), german.body(), "body of " + template);
    assertEquals(Locale.GERMAN, german.locale());
  }

  @ParameterizedTest
  @EnumSource(MailTemplate.class)
  void languageWithNoTemplateFallsBackToTheDefaultAndDoesNotThrow(MailTemplate template) {
    final RenderedMail french = render(template, Locale.FRENCH);
    final RenderedMail english = render(template, Locale.ENGLISH);
    assertEquals(english, french);
    assertEquals(MailRenderer.DEFAULT_LOCALE, french.locale());
  }

  @Test
  void regionalVariantKeepsItsLanguage() {
    final RenderedMail austrian = render(MailTemplate.EMAIL_VERIFICATION, Locale.GERMANY);
    assertEquals(Locale.GERMANY, austrian.locale());
    assertEquals(
        render(MailTemplate.EMAIL_VERIFICATION, Locale.GERMAN).subject(), austrian.subject());
  }

  @Test
  void theJvmDefaultLocaleMakesNoDifference() {
    final Locale original = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMAN);
      final RenderedMail english = render(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, Locale.ENGLISH);
      assertEquals("Order 080220220004 confirmed", english.subject());
      Locale.setDefault(Locale.ENGLISH);
      final RenderedMail german = render(MailTemplate.ORDER_CONFIRMATION_CUSTOMER, Locale.GERMAN);
      assertEquals("Bestellung 080220220004 bestätigt", german.subject());
    } finally {
      Locale.setDefault(original);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "ORDER_CONFIRMATION_CUSTOMER,ORDER_NOTIFICATION_STAFF",
    "INVOICE_CUSTOMER,INVOICE_STAFF",
    "HANDOFF_RECEIPT_CUSTOMER,HANDOFF_NOTIFICATION_STAFF",
    "INQUIRY_RECEIPT_CUSTOMER,INQUIRY_NOTIFICATION_STAFF"
  })
  void customerAndStaffVariantsCarryDifferentSubjectsAndBodies(
      MailTemplate customer, MailTemplate staff) {
    for (final Locale locale : new Locale[] {Locale.ENGLISH, Locale.GERMAN}) {
      final RenderedMail forCustomer = render(customer, locale);
      final RenderedMail forStaff = render(staff, locale);
      assertNotEquals(forCustomer.subject(), forStaff.subject());
      assertNotEquals(forCustomer.body(), forStaff.body());
    }
  }
}
