package com.rednavis.metaldesk.mail;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;

/**
 * The result of rendering a template: a subject and a body in one locale.
 *
 * <p>The locale is the one actually used, which is not always the one asked for: when a template
 * has no translation for the requested language, the renderer falls back to its default and says so
 * here, so the caller can tell.
 *
 * @param subject the subject line, on a single line and never blank
 * @param body the plain-text body, never blank
 * @param locale the locale the subject and body were rendered in
 */
public record RenderedMail(String subject, String body, Locale locale) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if the subject or body is null or blank, the subject spans more
   *     than one line, or the locale is null
   */
  public RenderedMail {
    require(subject, body, locale);
  }

  /**
   * Checks that a subject, body and locale form a valid rendered mail.
   *
   * @param subject the candidate subject
   * @param body the candidate body
   * @param locale the candidate locale
   * @throws ValidationException if the subject is not one non-blank line, the body is blank, or the
   *     locale is null
   */
  /* default */ static void require(String subject, String body, Locale locale) {
    requireOneLine(subject);
    if (body == null || body.isBlank() || locale == null) {
      throw new ValidationException(
          "rendered-mail.body-invalid", "Body must not be blank and a locale is required");
    }
  }

  private static void requireOneLine(String subject) {
    if (subject == null
        || subject.isBlank()
        || subject.indexOf('\n') >= 0
        || subject.indexOf('\r') >= 0) {
      throw new ValidationException(
          "rendered-mail.subject-invalid", "Subject must be one non-blank line");
    }
  }
}
