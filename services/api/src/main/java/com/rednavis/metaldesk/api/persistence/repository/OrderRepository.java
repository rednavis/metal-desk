package com.rednavis.metaldesk.api.persistence.repository;

import com.rednavis.metaldesk.persistence.document.OrderDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Reactive access to stored orders. */
public interface OrderRepository extends ReactiveMongoRepository<OrderDocument, String> {

  /**
   * Finds an order by its twelve-digit number, backed by the unique index.
   *
   * @param number the formatted order number
   * @return the order, or empty
   */
  Mono<OrderDocument> findByNumber(String number);

  /**
   * Lists a customer's orders, backed by the index on the customer id.
   *
   * @param customerId the customer id
   * @return the customer's orders
   */
  Flux<OrderDocument> findByCustomerId(String customerId);
}
