package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.persistence.fixtures.OrderFixtures;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.order.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import reactor.test.StepVerifier;

/** Orders survive a real trip through MongoDB, and the order-number unique index bites. */
class OrderPersistenceTest extends MongoTestSupport {

  @Autowired private OrderRepository repository;

  private final OrderMapper mapper = new OrderMapper();

  @Test
  void orderIsStoredAndReadBackEqualByNumberAndByCustomer() {
    final Order order = OrderFixtures.paidOrder("o-store-1", OrderFixtures.number(9001));

    StepVerifier.create(
            repository
                .save(mapper.toDocument(order))
                .thenMany(repository.findByNumber(order.number().format()))
                .map(mapper::toDomain))
        .expectNext(order)
        .verifyComplete();
    StepVerifier.create(repository.findByCustomerId("cust-1").map(OrderDocument::id))
        .expectNext("o-store-1")
        .verifyComplete();
  }

  @Test
  void secondOrderWithTheSameNumberIsRejected() {
    final Order first = OrderFixtures.paidOrder("o-dup-1", OrderFixtures.number(9002));
    final Order second = OrderFixtures.paidOrder("o-dup-2", OrderFixtures.number(9002));

    StepVerifier.create(
            repository
                .save(mapper.toDocument(first))
                .then(repository.save(mapper.toDocument(second))))
        .expectError(DuplicateKeyException.class)
        .verify();
  }
}
