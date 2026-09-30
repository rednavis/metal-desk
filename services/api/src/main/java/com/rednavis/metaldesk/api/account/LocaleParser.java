package com.rednavis.metaldesk.api.account;

import com.rednavis.metaldesk.mail.MailRenderer;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;

/** Reads the optional language tag of an account request. */
public final class LocaleParser {

  private static final int MAX_TAG = 35;

  private LocaleParser() {}

  /**
   * Reads a language tag.
   *
   * @param tag a BCP 47 tag such as {@code de}, or null or blank for the default
   * @return the locale, or {@link MailRenderer#DEFAULT_LOCALE} when none was given
   * @throws ValidationException if the tag is not a language tag
   */
  public static Locale parse(String tag) {
    Locale locale = MailRenderer.DEFAULT_LOCALE;
    if (tag != null && !tag.isBlank()) {
      if (tag.length() > MAX_TAG) {
        throw new ValidationException("locale.invalid", "Not a language tag");
      }
      locale = Locale.forLanguageTag(tag.strip());
      if (locale.getLanguage().isEmpty()) {
        throw new ValidationException("locale.invalid", "Not a language tag");
      }
    }
    return locale;
  }
}
