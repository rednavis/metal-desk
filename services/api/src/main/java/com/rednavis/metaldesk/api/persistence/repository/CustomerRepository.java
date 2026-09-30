package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.api.persistence.document.CustomerDocument;
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
}
