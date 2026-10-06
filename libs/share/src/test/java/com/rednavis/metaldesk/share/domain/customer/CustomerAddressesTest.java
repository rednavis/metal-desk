package com.rednavis.metaldesk.share.domain.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerAddressesTest {

  private static Address address(AddressKind kind, String street) {
    return new Address(kind, street, "Berlin", new Region("DE"), "10115", null, null);
  }

  private static Customer customer(List<Address> addresses) {
    return new Customer(
        new CustomerId("c-1"),
        "Ann Smith",
        new EmailAddress("ann@example.com"),
        null,
        addresses,
        VerificationState.UNVERIFIED);
  }

  @Test
  void nullAddressListOrElementIsRefused() {
    assertEquals(
        "customer.addresses-invalid",
        assertThrows(ValidationException.class, () -> customer(null)).code());
    assertEquals(
        "customer.addresses-invalid",
        assertThrows(ValidationException.class, () -> customer(Arrays.asList((Address) null)))
            .code());
  }

  @Test
  void addressListIsCopiedAndUnmodifiable() {
    final List<Address> source = new ArrayList<>(List.of(address(AddressKind.DELIVERY, "A 1")));
    final Customer customer = customer(source);
    source.add(address(AddressKind.BILLING, "B 2"));

    assertEquals(1, customer.addresses().size());
    final List<Address> held = customer.addresses();
    final Address extra = address(AddressKind.BILLING, "C 3");
    assertThrows(UnsupportedOperationException.class, () -> held.add(extra));
  }

  @Test
  void primaryAddressIsTheFirstOfItsKind() {
    final Address firstDelivery = address(AddressKind.DELIVERY, "A 1");
    final Address billing = address(AddressKind.BILLING, "B 2");
    final Address secondDelivery = address(AddressKind.DELIVERY, "C 3");
    final Customer customer = customer(List.of(firstDelivery, billing, secondDelivery));

    assertEquals(Optional.of(firstDelivery), customer.primaryAddress(AddressKind.DELIVERY));
    assertEquals(Optional.of(billing), customer.primaryAddress(AddressKind.BILLING));
  }

  @Test
  void primaryAddressIsEmptyNotNullWhenThereIsNoneOfThatKind() {
    final Customer noAddresses = customer(List.of());
    assertEquals(Optional.empty(), noAddresses.primaryAddress(AddressKind.DELIVERY));

    final Customer billingOnly = customer(List.of(address(AddressKind.BILLING, "B 2")));
    assertEquals(Optional.empty(), billingOnly.primaryAddress(AddressKind.DELIVERY));
  }
}
