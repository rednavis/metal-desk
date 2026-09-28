package com.rednavis.metaldesk.share.domain.customer;

import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The customer aggregate root (Architecture section 3): who is ordering, where to, and whether the
 * account may check out yet.
 *
 * <p>Compared with the legacy {@code Customer} document, the password is gone (it belongs to {@link
 * AuthCredential}), the single embedded address became a list of {@link Address}es tagged with an
 * {@link AddressKind}, and the phone is optional — FR-4.1 collects one at checkout, but
 * registration's "minimum viable profile" (FR-2.3) does not require it.
 *
 * <p>Addresses are a list, not a {@code deliveryAddress} / {@code billingAddress} pair, because a
 * customer with three saved delivery addresses is ordinary and FR-3.5 pre-fills "their saved
 * delivery profile". The list is copied defensively and is unmodifiable.
 *
 * @param id the customer's identifier, never null
 * @param name the customer's full name, trimmed and never blank
 * @param email the account email, never null
 * @param phone the phone number, or {@code null} when none was given
 * @param addresses the saved addresses, possibly empty, never null and holding no null element
 * @param verification whether the email has been verified, never null; see {@link
 *     VerificationState#canCheckout()}
 */
public record Customer(
    CustomerId id,
    String name,
    EmailAddress email,
    PhoneNumber phone,
    List<Address> addresses,
    VerificationState verification) {

  /**
   * Validates the fields and copies the address list.
   *
   * @throws ValidationException if the id, email, address list or verification state is null, the
   *     name is null or blank, or the address list holds a null element
   */
  public Customer {
    if (id == null) {
      throw new ValidationException("customer.id-missing", "Customer id must not be null");
    }
    if (name == null || name.isBlank()) {
      throw new ValidationException(
          "customer.name-blank", "Customer name must not be null or blank");
    }
    name = name.strip();
    if (email == null) {
      throw new ValidationException("customer.email-missing", "Customer email must not be null");
    }
    if (addresses == null || addresses.stream().anyMatch(Objects::isNull)) {
      throw new ValidationException(
          "customer.addresses-invalid", "Customer addresses must not be null or hold a null");
    }
    addresses = List.copyOf(addresses);
    if (verification == null) {
      throw new ValidationException(
          "customer.verification-missing", "Customer verification state must not be null");
    }
  }

  /**
   * Returns the customer's primary address of a kind: the first saved address of that kind.
   *
   * @param kind the kind of address wanted
   * @return the first address of that kind, or empty when the customer has none
   */
  public Optional<Address> primaryAddress(AddressKind kind) {
    return addresses.stream().filter(address -> address.kind() == kind).findFirst();
  }
}
