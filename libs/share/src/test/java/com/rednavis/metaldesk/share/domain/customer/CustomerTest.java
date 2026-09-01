package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTest {

  private static final CustomerId CUSTOMER_ID = new CustomerId("c-1");
  private static final EmailAddress EMAIL = new EmailAddress("ann@example.com");

  private static Customer customer(List<Address> addresses) {
    return new Customer(
        CUSTOMER_ID, "Ann Smith", EMAIL, null, addresses, VerificationState.UNVERIFIED);
  }

  @Test
  void phoneIsOptional() {
    final Customer customer = customer(List.of());
    assertNull(customer.phone());
    assertEquals("Ann Smith", customer.name());
  }

  @Test
  void nameIsTrimmed() {
    final Customer customer =
        new Customer(
            CUSTOMER_ID, "  Ann Smith ", EMAIL, null, List.of(), VerificationState.VERIFIED);
    assertEquals("Ann Smith", customer.name());
  }

  @Test
  void nullIdIsRefused() {
    assertEquals(
        "customer.id-missing",
        assertThrows(
                ValidationException.class,
                () -> new Customer(null, "Ann", EMAIL, null, List.of(), VerificationState.VERIFIED))
            .code());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankNameIsRefused(String name) {
    assertEquals(
        "customer.name-blank",
        assertThrows(
                ValidationException.class,
                () ->
                    new Customer(
                        CUSTOMER_ID, name, EMAIL, null, List.of(), VerificationState.VERIFIED))
            .code());
  }

  @Test
  void nullEmailIsRefused() {
    assertEquals(
        "customer.email-missing",
        assertThrows(
                ValidationException.class,
                () ->
                    new Customer(
                        CUSTOMER_ID, "Ann", null, null, List.of(), VerificationState.VERIFIED))
            .code());
  }

  @Test
  void nullVerificationIsRefused() {
    assertEquals(
        "customer.verification-missing",
        assertThrows(
                ValidationException.class,
                () -> new Customer(CUSTOMER_ID, "Ann", EMAIL, null, List.of(), null))
            .code());
  }
}
