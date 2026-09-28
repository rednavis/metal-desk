package com.rednavis.metaldesk.mail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the mail template files from the classpath, once, and checks their layout.
 *
 * <p>A template file is UTF-8 text under {@code mail/}, named {@code <template>_<language>.txt}.
 * Its first line is {@code Subject: } and the subject, then a blank line, then the body. Loading is
 * strict about the layout and about the default language, so a broken or missing template fails
 * when the {@link MailRenderer} is created, not when a customer is waiting for an email.
 */
final class TemplateLibrary {

  /** The languages that have template files, by language code. */
  private static final List<String> LANGUAGES = List.of("en", "de");

  private static final String DEFAULT_LANGUAGE = "en";
  private static final String SUBJECT_PREFIX = "Subject: ";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9]*)}}");

  private TemplateLibrary() {}

  /**
   * One template file, parsed.
   *
   * @param subject the subject line, with its placeholders
   * @param body the body, with its placeholders
   * @param placeholders the names of every placeholder in the subject and body
   */
  /* default */ record ParsedTemplate(String subject, String body, Set<String> placeholders) {}

  /**
   * Reads every template in every language that has a file.
   *
   * @return for each template, its files by language code
   * @throws IllegalStateException if a template has no default-language file, or a file is
   *     malformed
   */
  /* default */ static Map<MailTemplate, Map<String, ParsedTemplate>> load() {
    return Arrays.stream(MailTemplate.values())
        .collect(
            Collectors.toMap(
                Function.identity(),
                TemplateLibrary::loadLanguages,
                (first, second) -> first,
                () -> new EnumMap<>(MailTemplate.class)));
  }

  private static Map<String, ParsedTemplate> loadLanguages(MailTemplate template) {
    final Map<String, ParsedTemplate> byLanguage =
        LANGUAGES.stream()
            .flatMap(language -> loadOne(template, language).stream())
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    if (!byLanguage.containsKey(DEFAULT_LANGUAGE)) {
      throw new IllegalStateException("No default-language template for " + template);
    }
    return byLanguage;
  }

  private static Optional<Map.Entry<String, ParsedTemplate>> loadOne(
      MailTemplate template, String language) {
    final String resource = "/mail/" + template.resourceName() + "_" + language + ".txt";
    return readResource(resource).map(raw -> Map.entry(language, parse(raw)));
  }

  private static Optional<String> readResource(String resource) {
    try (InputStream stream = TemplateLibrary.class.getResourceAsStream(resource)) {
      return stream == null
          ? Optional.empty()
          : Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Could not read mail template " + resource, e);
    }
  }

  private static ParsedTemplate parse(String raw) {
    final String text = raw.replace("\r\n", "\n");
    final int firstBreak = text.indexOf('\n');
    requireLayout(text, firstBreak);
    final String subject = text.substring(SUBJECT_PREFIX.length(), firstBreak).strip();
    final String body = text.substring(firstBreak + 2).strip();
    if (subject.isEmpty() || body.isEmpty()) {
      throw new IllegalStateException("A mail template needs a subject and a body");
    }
    return new ParsedTemplate(subject, body, placeholdersOf(subject, body));
  }

  private static void requireLayout(String text, int firstBreak) {
    if (firstBreak < 0
        || !text.startsWith(SUBJECT_PREFIX)
        || !text.startsWith("\n", firstBreak + 1)) {
      throw new IllegalStateException(
          "A mail template is a 'Subject: ' line, a blank line, then the body");
    }
  }

  private static Set<String> placeholdersOf(String subject, String body) {
    return Stream.of(subject, body)
        .flatMap(part -> PLACEHOLDER.matcher(part).results())
        .map(match -> match.group(1))
        .collect(Collectors.toUnmodifiableSet());
  }
}
