package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.api.persistence.mapper.CustomerMapper;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.share.domain.customer.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import reactor.test.StepVerifier;

/** Customers survive a real trip through MongoDB, and their email is unique. */
class CustomerPersistenceTest extends MongoTestSupport {

  @Autowired private CustomerRepository repository;

  private final CustomerMapper mapper = new CustomerMapper();

  @Test
  void customerIsStoredAndReadBackEqualByEmail() {
    final Customer customer =
        AccountFixtures.customer("c-store-1", "store@example.com", "+49301234567");

    StepVerifier.create(
            repository
                .save(mapper.toDocument(customer))
                .then(repository.findByEmail("store@example.com"))
                .map(mapper::toDomain))
        .expectNext(customer)
        .verifyComplete();
  }

  @Test
  void secondCustomerWithTheSameEmailIsRejected() {
    final Customer first = AccountFixtures.customer("c-dup-1", "dup@example.com", null);
    final Customer second = AccountFixtures.customer("c-dup-2", "dup@example.com", null);

    StepVerifier.create(
            repository
                .save(mapper.toDocument(first))
                .then(repository.save(mapper.toDocument(second))))
        .expectError(DuplicateKeyException.class)
        .verify();
  }
}
