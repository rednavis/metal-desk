package com.rednavis.metaldesk.persistence.mapper;

import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import com.rednavis.metaldesk.share.domain.customer.Customer;
import com.rednavis.metaldesk.share.domain.customer.EmailAddress;
import com.rednavis.metaldesk.share.domain.customer.PhoneNumber;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import org.springframework.stereotype.Component;

/** Maps a {@link Customer} to its {@link CustomerDocument} and back. */
@Component
public class CustomerMapper {

  /**
   * Rebuilds the domain customer.
   *
   * @param document the stored customer
   * @return the customer
   */
  public Customer toDomain(CustomerDocument document) {
    return new Customer(
        new CustomerId(document.id()),
        document.name(),
        new EmailAddress(document.email()),
        document.phone() == null ? null : new PhoneNumber(document.phone()),
        document.addresses().stream().map(ValueMapper::addressToDomain).toList(),
        document.verification());
  }

  /**
   * Builds the document to store.
   *
   * @param customer the customer
   * @return the document
   */
  public CustomerDocument toDocument(Customer customer) {
    return new CustomerDocument(
        customer.id().value(),
        customer.name(),
        customer.email().value(),
        customer.phone() == null ? null : customer.phone().value(),
        customer.addresses().stream().map(ValueMapper::addressToDocument).toList(),
        customer.verification());
  }
}
