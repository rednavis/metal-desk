package com.rednavis.metaldesk.mail;

import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a {@link MailTemplate}, a model and a locale into a localized subject and body.
 *
 * <p><strong>The locale is always an explicit parameter.</strong> Nothing here reads the JVM's
 * default locale, a thread-local or any other ambient state, so an email renders in the customer's
 * language wherever the service runs (BRD FR-1.7, FR-6.2). A test changes the JVM default and shows
 * it makes no difference.
 *
 * <p><strong>A missing translation falls back; it does not fail.</strong> If a template has no file
 * for the requested language, the {@linkplain #DEFAULT_LOCALE default locale} is rendered instead
 * and a warning is logged, because a gap in a translation must not fail an order. The {@link
 * RenderedMail} says which locale was actually used. A missing <em>default</em> template is a build
 * error: loading fails when the renderer is created.
 *
 * <p><strong>Template files</strong> are UTF-8 text under {@code mail/} on the classpath, named
 * {@code <template>_<language>.txt}. The first line is {@code Subject: } and the subject, then a
 * blank line, then the body. {@code {{name}}} is replaced by the model value called {@code name}; a
 * {@link Money} is formatted for the locale used, anything else by its string form. A placeholder
 * the model does not supply is a {@link ValidationException}, because sending "your code is
 * {{code}}" to a customer is worse than not sending. Values are inserted as they are, but a subject
 * is reduced to one line so a value cannot inject a header. Subjects are localized like bodies.
 *
 * <p>The template files are read once, when the renderer is created, so rendering never touches the
 * file system.
 */
@Slf4j
public final class MailRenderer {

  /** The locale rendered when the requested language has no template. */
  public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

  private final Map<MailTemplate, Map<String, TemplateLibrary.ParsedTemplate>> templates;

  /**
   * Creates a renderer, reading every template file.
   *
   * @throws IllegalStateException if a template has no file in the default language, or a file is
   *     malformed; this is a build error, and the template-set test catches it
   */
  public MailRenderer() {
    this.templates = TemplateLibrary.load();
  }

  /**
   * Renders a template.
   *
   * @param template which notification to render
   * @param model the values for the template's placeholders; extra entries are ignored
   * @param locale the customer's locale, never taken from the environment
   * @return the localized subject and body, and the locale actually used
   * @throws ValidationException if an argument is null, or the model lacks a value the template
   *     needs
   */
  public RenderedMail render(MailTemplate template, Map<String, Object> model, Locale locale) {
    if (template == null || model == null || locale == null) {
      throw new ValidationException(
          "mail-renderer.input-missing", "Rendering needs a template, a model and a locale");
    }
    final Map<String, TemplateLibrary.ParsedTemplate> languages = templates.get(template);
    final boolean translated = languages.containsKey(locale.getLanguage());
    if (!translated) {
      warnMissing(template, locale);
    }
    final Locale used = translated ? locale : DEFAULT_LOCALE;
    final TemplateLibrary.ParsedTemplate chosen =
        languages.get(translated ? locale.getLanguage() : "en");
    requireModel(chosen, model);
    return new RenderedMail(
        oneLine(fill(chosen.subject(), model, used)), fill(chosen.body(), model, used), used);
  }

  private static void warnMissing(MailTemplate template, Locale locale) {
    if (log.isWarnEnabled()) {
      log.warn(
          "No {} mail template for language '{}'; rendering the default instead",
          template,
          locale.getLanguage());
    }
  }

  private static void requireModel(
      TemplateLibrary.ParsedTemplate template, Map<String, Object> model) {
    final Set<String> missing = new TreeSet<>();
    for (final String key : template.placeholders()) {
      if (model.get(key) == null) {
        missing.add(key);
      }
    }
    if (!missing.isEmpty()) {
      throw new ValidationException(
          "mail-renderer.model-missing", "The mail model lacks values for " + missing);
    }
  }

  private static String fill(String text, Map<String, Object> model, Locale locale) {
    return PLACEHOLDER
        .matcher(text)
        .replaceAll(match -> Matcher.quoteReplacement(format(model.get(match.group(1)), locale)));
  }

  private static String format(Object value, Locale locale) {
    String formatted = String.valueOf(value);
    if (value instanceof Money money) {
      final NumberFormat currency = NumberFormat.getCurrencyInstance(locale);
      currency.setCurrency(java.util.Currency.getInstance(money.currency().code()));
      formatted = currency.format(money.amount());
    }
    return formatted;
  }

  private static String oneLine(String subject) {
    return subject.replaceAll("[\\r\\n]+", " ").strip();
  }
}
