package com.rednavis.metaldesk.api.account.preferences;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Display preferences, as sent and received. A field the customer has not chosen is absent: null
 * means "follow the device", never a default value.
 *
 * @param theme {@code LIGHT} or {@code DARK}
 * @param locale a supported language tag, {@code en} or {@code de}
 * @param currency a display currency code the catalog can be shown in
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreferencesView(String theme, String locale, String currency) {}
