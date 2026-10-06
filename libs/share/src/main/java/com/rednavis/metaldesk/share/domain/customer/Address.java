package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * A structured postal address (BRD FR-4.1).
 *
 * <p>Street, city, country and postal code are mandatory; the company name and company address are
 * optional. The legacy model split the street from a separate {@code house} field, kept a free-text
 * {@code country} and carried unused {@code line1}, {@code line2} and {@code state} fields; here
 * the street holds the whole street line, and the country is a typed {@link Region}.
 *
 * <p><strong>The country is a {@link Region}, not a string.</strong> Fulfillment-tier lookup binds
 * on region (FR-5.1), so an address whose country cannot become a {@code Region} is refused when it
 * is built — by {@code Region}'s own validation — instead of surprising the checkout when the tier
 * is looked up. Whether a well-formed region is one staff have configured tiers for is checked
 * where tiers are stored.
 *
 * <p>Text fields are trimmed. A blank company name or company address is treated as absent and kept
 * as {@code null}.
 *
 * @param kind whether this is a delivery or a billing address
 * @param street the street line, never blank
 * @param city the city, never blank
 * @param country the delivery region the address resolves to, never null
 * @param postalCode the postal code, never blank
 * @param companyName the company name, or {@code null} when the address has none
 * @param companyAddress the company's own address, or {@code null} when it has none
 */
public record Address(
    AddressKind kind,
    String street,
    String city,
    Region country,
    String postalCode,
    String companyName,
    String companyAddress) {

  /**
   * Validates and normalises the fields.
   *
   * @throws ValidationException if the kind or country is null, or the street, city or postal code
   *     is null or blank
   */
  public Address {
    if (kind == null) {
      throw new ValidationException("address.kind-missing", "Address kind must not be null");
    }
    street = requireText(street, "address.street-blank", "Street");
    city = requireText(city, "address.city-blank", "City");
    if (country == null) {
      throw new ValidationException("address.country-missing", "Country must not be null");
    }
    postalCode = requireText(postalCode, "address.postal-code-blank", "Postal code");
    companyName = optionalText(companyName);
    companyAddress = optionalText(companyAddress);
  }

  private static String requireText(String value, String code, String field) {
    if (value == null || value.isBlank()) {
      throw new ValidationException(code, field + " must not be null or blank");
    }
    return value.strip();
  }

  private static String optionalText(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
