package com.rednavis.metaldesk.api.checkout.step1;

import com.rednavis.metaldesk.share.domain.customer.Address;
import com.rednavis.metaldesk.share.domain.customer.AddressKind;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.Optional;

/**
 * The validated customer and delivery data of checkout step 1 (BRD FR-4.1).
 *
 * <p>The mandatory fields are the name, email, phone and a structured delivery address (street,
 * city, country, postal code); the optional ones are the company name and address, which live on
 * the {@link Address}, and an order note. Every mandatory field has been checked for presence
 * <em>and</em> format before one of these exists.
 *
 * @param name the customer's name, never blank
 * @param email the email address
 * @param phone the phone number
 * @param deliveryAddress the delivery address, of kind {@code DELIVERY}
 * @param note the order note, if any
 */
public record CustomerDetails(
    String name,
    EmailAddress email,
    PhoneNumber phone,
    Address deliveryAddress,
    Optional<String> note) {

  /**
   * Validates the fields.
   *
   * @throws ValidationException if a field is missing, the name is blank or the address is not a
   *     delivery address
   */
  public CustomerDetails {
    if (name == null
        || name.isBlank()
        || email == null
        || phone == null
        || deliveryAddress == null
        || note == null) {
      throw new ValidationException(
          "customer-details.field-missing",
          "Customer details need a name, email, phone, delivery address and note optional");
    }
    if (deliveryAddress.kind() != AddressKind.DELIVERY) {
      throw new ValidationException(
          "customer-details.address-invalid", "The address must be a delivery address");
    }
    name = name.strip();
  }
}
