package com.rednavis.metaldesk.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.api.persistence.document.OrderSequenceDocument;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** The daily order-number sequence: distinct under concurrency, restarting each day. */
class OrderNumberSequenceTest extends MongoTestSupport {

  private static final int ALLOCATIONS = 400;
  private static final int SUBSCRIBERS = 8;

  @Autowired private OrderNumberSequence sequence;
  @Autowired private ReactiveMongoTemplate mongo;

  @Test
  void concurrentSubscribersAreNeverIssuedTheSameNumber() {
    final LocalDate day = LocalDate.of(2031, 3, 1);

    final List<OrderNumber> numbers =
        Flux.range(0, ALLOCATIONS)
            .parallel(SUBSCRIBERS)
            .runOn(Schedulers.boundedElastic())
            .flatMap(ignored -> sequence.next(day))
            .sequential()
            .collectList()
            .block();

    assertEquals(ALLOCATIONS, numbers.size());
    final Set<String> distinct =
        numbers.stream().map(OrderNumber::format).collect(Collectors.toSet());
    assertEquals(ALLOCATIONS, distinct.size(), "an order number was issued twice");
    // Nothing failed, so nothing was skipped: the sequence is exactly 1..N.
    final Set<Integer> sequences =
        numbers.stream().map(OrderNumber::sequence).collect(Collectors.toSet());
    assertEquals(
        IntStream.rangeClosed(1, ALLOCATIONS).boxed().collect(Collectors.toSet()), sequences);
  }

  @Test
  void differentDayRestartsTheSequence() {
    final LocalDate first = LocalDate.of(2031, 4, 1);
    final LocalDate second = LocalDate.of(2031, 4, 2);

    StepVerifier.create(sequence.next(first).then(sequence.next(first)).then(sequence.next(second)))
        .assertNext(number -> assertEquals(new OrderNumber(second, 1), number))
        .verifyComplete();
    StepVerifier.create(sequence.next(first))
        .assertNext(number -> assertEquals(new OrderNumber(first, 3), number))
        .verifyComplete();
  }

  @Test
  void exhaustedDayIsRefusedNotMalformed() {
    final LocalDate day = LocalDate.of(2031, 5, 1);

    StepVerifier.create(
            mongo
                .insert(new OrderSequenceDocument(day.toString(), OrderNumber.MAX_SEQUENCE))
                .then(sequence.next(day)))
        .expectErrorSatisfies(
            error -> {
              assertTrue(error instanceof ConflictException);
              assertEquals("order-number.exhausted", ((ConflictException) error).code());
            })
        .verify();
  }
}
