package com.rednavis.metaldesk.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionalMailTest {

  private static final String SUBJECT = "Subject";
  private static final MailTemplate TEMPLATE = MailTemplate.INVOICE_CUSTOMER;
  private static final List<EmailAddress> TO = List.of(MailFixtures.CUSTOMER);

  private static TransactionalMail mail(
      List<EmailAddress> to, String subject, String body, List<MailAttachment> attachments) {
    return new TransactionalMail(TEMPLATE, to, subject, body, Locale.ENGLISH, attachments);
  }

  @Test
  void carriesItsTemplateRecipientsLocaleAndAttachments() {
    final MailAttachment invoice =
        new MailAttachment(
            "invoice.pdf", "application/pdf", "%PDF-test".getBytes(StandardCharsets.US_ASCII));
    final TransactionalMail mail = mail(TO, "Invoice", "Attached.", List.of(invoice));
    assertEquals(TEMPLATE, mail.template());
    assertEquals(TO, mail.to());
    assertEquals(Locale.ENGLISH, mail.locale());
    assertEquals(List.of(invoice), mail.attachments());
  }

  @Test
  void listsAreCopiedSoTheMailCannotChangeAfterwards() {
    final List<EmailAddress> recipients = new ArrayList<>(TO);
    final TransactionalMail mail = mail(recipients, SUBJECT, "Body", List.of());
    recipients.clear();
    assertEquals(TO, mail.to());
    assertThrows(UnsupportedOperationException.class, () -> mail.to().clear());
  }

  @Test
  void renderedMailBecomesTransactionalMail() {
    final RenderedMail rendered = new RenderedMail(SUBJECT, "Body", Locale.GERMAN);
    final TransactionalMail mail = TransactionalMail.from(TEMPLATE, rendered, TO, List.of());
    assertEquals(SUBJECT, mail.subject());
    assertEquals(Locale.GERMAN, mail.locale());
  }

  @Test
  void mailNeedsAtLeastOneRecipientAndNoNullOne() {
    assertEquals(
        "transactional-mail.recipients-invalid",
        assertThrows(ValidationException.class, () -> mail(List.of(), "S", "B", List.of())).code());
    assertEquals(
        "transactional-mail.recipients-invalid",
        assertThrows(
                ValidationException.class,
                () -> mail(Arrays.asList(MailFixtures.CUSTOMER, null), "S", "B", List.of()))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "two\nlines", "carriage\rreturn"})
  void subjectMustBeOneNonBlankLine(String subject) {
    assertEquals(
        "rendered-mail.subject-invalid",
        assertThrows(ValidationException.class, () -> mail(TO, subject, "Body", List.of())).code());
  }

  @Test
  void bodyTemplateAndLocaleAreRequired() {
    assertEquals(
        "rendered-mail.body-invalid",
        assertThrows(ValidationException.class, () -> mail(TO, SUBJECT, " ", List.of())).code());
    assertEquals(
        "transactional-mail.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new TransactionalMail(null, TO, "S", "B", Locale.ENGLISH, List.of()))
            .code());
    assertEquals(
        "transactional-mail.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new TransactionalMail(TEMPLATE, TO, "S", "B", null, List.of()))
            .code());
  }
}
