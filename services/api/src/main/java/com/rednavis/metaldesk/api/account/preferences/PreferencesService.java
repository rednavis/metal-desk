package com.rednavis.metaldesk.api.account.preferences;

import com.rednavis.metaldesk.api.currency.DisplayCurrencies;
import com.rednavis.metaldesk.api.persistence.repository.PreferencesRepository;
import com.rednavis.metaldesk.persistence.document.PreferencesDocument;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Reads and replaces a customer's display preferences (BRD FR-1.6 to FR-1.8).
 *
 * <p>Saving replaces all three fields at once, because the client always sends the whole preference
 * and a partial merge would make "clear my currency" inexpressible. Each given value is checked
 * against what the platform can actually do: a theme it has, a language it has mail templates for,
 * a currency it can convert to.
 */
@Service
@RequiredArgsConstructor
public class PreferencesService {

  private static final Set<String> THEMES = Set.of("LIGHT", "DARK");
  private static final Set<String> LANGUAGES = Set.of("en", "de");

  private final PreferencesRepository repository;
  private final DisplayCurrencies currencies;

  /**
   * Reads a customer's preferences.
   *
   * @param customer the customer
   * @return what they chose; all fields absent if they chose nothing
   */
  public Mono<PreferencesView> read(CustomerId customer) {
    return repository
        .findById(customer.value())
        .map(PreferencesService::view)
        .defaultIfEmpty(new PreferencesView(null, null, null));
  }

  /**
   * Replaces a customer's preferences.
   *
   * @param customer the customer
   * @param request the new preferences
   * @return what was stored
   * @throws ValidationException {@code preferences.invalid} for a value the platform cannot honour
   */
  public Mono<PreferencesView> save(CustomerId customer, PreferencesView request) {
    final PreferencesView checked = check(request);
    return repository
        .save(
            new PreferencesDocument(
                customer.value(), checked.theme(), checked.locale(), checked.currency()))
        .map(PreferencesService::view);
  }

  private PreferencesView check(PreferencesView request) {
    if (request == null) {
      throw new ValidationException("preferences.invalid", "Preferences are required");
    }
    return new PreferencesView(
        theme(request.theme()), language(request.locale()), currency(request.currency()));
  }

  private static String theme(String requested) {
    final String theme = requested == null ? null : requested.toUpperCase(Locale.ROOT);
    if (theme != null && !THEMES.contains(theme)) {
      throw new ValidationException("preferences.invalid", "Unknown theme " + requested);
    }
    return theme;
  }

  private static String language(String requested) {
    final String language = requested == null ? null : requested.toLowerCase(Locale.ROOT);
    if (language != null && !LANGUAGES.contains(language)) {
      throw new ValidationException("preferences.invalid", "Unsupported language " + requested);
    }
    return language;
  }

  private String currency(String requested) {
    return requested == null ? null : currencies.resolve(requested).code();
  }

  private static PreferencesView view(PreferencesDocument document) {
    return new PreferencesView(document.theme(), document.locale(), document.currency());
  }
}
