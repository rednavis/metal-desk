package com.rednavis.metaldesk.api.persistence;

import com.rednavis.metaldesk.persistence.document.OrderSequenceDocument;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.error.ConflictException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Allocates order numbers: {@code <date><daily-sequence>} (BRD BR-6), with the sequence restarting
 * at 1 each day.
 *
 * <p><strong>Atomic, never read-then-write.</strong> Each allocation is one {@code findAndModify}
 * that increments the day's counter document and returns the new value, creating the document at 1
 * on the day's first order. Two checkouts landing together therefore cannot see the same value; a
 * read followed by a write would issue both of them the same number.
 *
 * <p><strong>Policy: gaps are allowed, reuse never is.</strong> A number is consumed the moment it
 * is allocated. If the order it was for then fails to save, that number is simply skipped. Handing
 * it out again would need a second, racy piece of bookkeeping to be sure nobody used it, and BR-6
 * only promises numbers that are monotonic within a day, not gapless ones. The unique index on
 * {@code OrderDocument.number} backs this up if a caller ever bypasses the sequence.
 *
 * <p>A day holds at most {@link OrderNumber#MAX_SEQUENCE} orders. The next allocation fails with a
 * {@link ConflictException} rather than a malformed number.
 */
@Component
@RequiredArgsConstructor
public class OrderNumberSequence {

  private static final long KEY_RETRIES = 3;

  private final ReactiveMongoTemplate mongo;

  /**
   * Allocates the next order number for a day.
   *
   * <p>The day is an argument because the domain has no clock; the caller decides which day an
   * order belongs to.
   *
   * @param date the day the order is placed
   * @return the next number for that day, never one already allocated
   */
  public Mono<OrderNumber> next(LocalDate date) {
    return mongo
        .findAndModify(
            Query.query(Criteria.where("_id").is(date.toString())),
            new Update().inc("value", 1),
            FindAndModifyOptions.options().upsert(true).returnNew(true),
            OrderSequenceDocument.class)
        // Two first-of-the-day upserts can race on the new document's _id; the loser gets a
        // duplicate-key error, and repeating the call then finds the document and increments it.
        .retryWhen(Retry.max(KEY_RETRIES).filter(DuplicateKeyException.class::isInstance))
        .flatMap(counter -> toNumber(date, counter.value()));
  }

  private static Mono<OrderNumber> toNumber(LocalDate date, long value) {
    return value > OrderNumber.MAX_SEQUENCE
        ? Mono.error(
            new ConflictException(
                "order-number.exhausted",
                "No order numbers left for "
                    + date
                    + ": the day holds at most "
                    + OrderNumber.MAX_SEQUENCE))
        : Mono.just(new OrderNumber(date, Math.toIntExact(value)));
  }
}
