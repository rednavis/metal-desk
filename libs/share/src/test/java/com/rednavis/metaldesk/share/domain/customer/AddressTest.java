package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AddressTest {

  private static final Region GERMANY = new Region("de");
  private static final String STREET = "Main St 1";
  private static final String CITY = "Berlin";
  private static final String POSTAL_CODE = "10115";

  private static Address address(String street, String city, Region country, String postalCode) {
    return new Address(AddressKind.DELIVERY, street, city, country, postalCode, null, null);
  }

  @Test
  void mandatoryFieldsAreKeptAndTrimmed() {
    final Address address = address(" Main St 1 ", " Berlin ", GERMANY, " 10115 ");
    assertEquals(STREET, address.street());
    assertEquals(CITY, address.city());
    assertEquals(GERMANY, address.country());
    assertEquals(POSTAL_CODE, address.postalCode());
    assertEquals(AddressKind.DELIVERY, address.kind());
  }

  @Test
  void companyFieldsAreOptional() {
    final Address without = address(STREET, CITY, GERMANY, POSTAL_CODE);
    assertNull(without.companyName());
    assertNull(without.companyAddress());

    final Address blank =
        new Address(AddressKind.BILLING, STREET, CITY, GERMANY, POSTAL_CODE, "  ", "");
    assertNull(blank.companyName());
    assertNull(blank.companyAddress());

    final Address with =
        new Address(AddressKind.BILLING, STREET, CITY, GERMANY, POSTAL_CODE, " ACME ", " HQ 2 ");
    assertEquals("ACME", with.companyName());
    assertEquals("HQ 2", with.companyAddress());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankStreetIsRefused(String street) {
    assertEquals(
        "address.street-blank",
        assertThrows(ValidationException.class, () -> address(street, CITY, GERMANY, POSTAL_CODE))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankCityIsRefused(String city) {
    assertEquals(
        "address.city-blank",
        assertThrows(ValidationException.class, () -> address(STREET, city, GERMANY, POSTAL_CODE))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void blankPostalCodeIsRefused(String postalCode) {
    assertEquals(
        "address.postal-code-blank",
        assertThrows(ValidationException.class, () -> address(STREET, CITY, GERMANY, postalCode))
            .code());
  }

  @Test
  void missingCountryIsRefused() {
    assertEquals(
        "address.country-missing",
        assertThrows(ValidationException.class, () -> address(STREET, CITY, null, POSTAL_CODE))
            .code());
  }

  @Test
  void countryThatIsNotValidRegionCannotBeBuilt() {
    // The country is typed: a code that is not a valid Region fails before an Address exists.
    assertThrows(
        ValidationException.class,
        () -> address(STREET, CITY, new Region("not a region"), POSTAL_CODE));
  }

  @Test
  void missingKindIsRefused() {
    assertEquals(
        "address.kind-missing",
        assertThrows(
                ValidationException.class,
                () -> new Address(null, STREET, CITY, GERMANY, POSTAL_CODE, null, null))
            .code());
  }
}
