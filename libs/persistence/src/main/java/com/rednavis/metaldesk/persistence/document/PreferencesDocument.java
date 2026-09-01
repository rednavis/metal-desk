package com.rednavis.metaldesk.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A customer's display preferences (BRD FR-1.6 to FR-1.8), kept so they follow the account across
 * devices. Each field is absent until the customer chooses it.
 *
 * @param customerId the customer; one document per customer
 * @param theme {@code LIGHT} or {@code DARK}, or null to follow the device
 * @param locale a language tag such as {@code de}, or null
 * @param currency a display currency code such as {@code USD}, or null
 */
@Document("preferences")
public record PreferencesDocument(
    @Id String customerId, String theme, String locale, String currency) {}
