package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.customer.Customer;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.customer.VerificationState;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import java.util.List;

/** Small synthetic customer objects for the persistence tests. */
public final class AccountFixtures {

  private AccountFixtures() {}

  /** A delivery address, with company details. */
  public static Address deliveryAddress() {
    return new Address(
        AddressKind.DELIVERY,
        "1 Main Street",
        "Berlin",
        new Region("de"),
        "10115",
        "Example GmbH",
        "2 Side Street, Berlin");
  }

  /** A verified customer with one delivery address. */
  public static Customer customer(String id, String email, String phone) {
    return new Customer(
        new CustomerId(id),
        "Ann Example",
        new EmailAddress(email),
        phone == null ? null : new PhoneNumber(phone),
        List.of(deliveryAddress()),
        VerificationState.VERIFIED);
  }
}
