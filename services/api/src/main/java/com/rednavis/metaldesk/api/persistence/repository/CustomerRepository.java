package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.CustomerDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/** Reactive access to stored customers. */
public interface CustomerRepository extends ReactiveMongoRepository<CustomerDocument, String> {

  /**
   * Finds a customer by normalised email address, backed by the unique index.
   *
   * @param email the lower-cased email address
   * @return the customer, or empty
   */
  Mono<CustomerDocument> findByEmail(String email);

  /**
   * Finds a customer by normalised phone number, backed by the sparse index.
   *
   * <p>Phone numbers are not unique (T-033 decides whether they should be), so if several customers
   * share one this returns an arbitrary one of them.
   *
   * @param phone the normalised phone number
   * @return a customer with that number, or empty
   */
  Mono<CustomerDocument> findFirstByPhone(String phone);
}
