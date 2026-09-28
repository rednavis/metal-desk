package com.rednavis.metaldesk.mail;

import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One mail, ready to send: who it goes to, what it says, in which locale, with which attachments,
 * and which template it came from.
 *
 * <p>The template id is kept so a sent mail can be identified by what it is, which is how tests and
 * support tell a verification code from an order confirmation. The subject must be a single line:
 * it goes into a mail header, and a line break in it would inject another. Lists are copied, so the
 * mail cannot change after it is built.
 *
 * @param template which notification this is, never null
 * @param to the recipients, at least one, holding no null element
 * @param subject the subject line, one non-blank line
 * @param body the plain-text body, never blank
 * @param locale the locale the mail was rendered in, never null
 * @param attachments the attached files, possibly empty, holding no null element
 */
public record TransactionalMail(
    MailTemplate template,
    List<EmailAddress> to,
    String subject,
    String body,
    Locale locale,
    List<MailAttachment> attachments) {

  /**
   * Validates the fields and copies the lists.
   *
   * @throws ValidationException if the template or locale is null, there is no recipient or a null
   *     element, the subject is not one non-blank line, the body is blank, or the attachments hold
   *     a null
   */
  public TransactionalMail {
    if (template == null || locale == null) {
      throw new ValidationException(
          "transactional-mail.field-missing", "A mail needs a template and a locale");
    }
    if (to == null || to.isEmpty() || to.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "transactional-mail.recipients-invalid",
          "A mail needs at least one recipient and no null recipient");
    }
    to = List.copyOf(to);
    RenderedMail.require(subject, body, locale);
    if (attachments == null || attachments.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "transactional-mail.attachments-invalid", "Attachments must be present and hold no null");
    }
    attachments = List.copyOf(attachments);
  }

  /**
   * Builds a mail from a rendered template.
   *
   * @param template the template that was rendered
   * @param rendered the rendered subject, body and locale
   * @param to the recipients
   * @param attachments the attached files, possibly empty
   * @return the mail
   * @throws ValidationException if a field is invalid
   */
  public static TransactionalMail from(
      MailTemplate template,
      RenderedMail rendered,
      List<EmailAddress> to,
      List<MailAttachment> attachments) {
    if (rendered == null) {
      throw new ValidationException(
          "transactional-mail.field-missing", "A mail needs its rendered content");
    }
    return new TransactionalMail(
        template, to, rendered.subject(), rendered.body(), rendered.locale(), attachments);
  }
}
