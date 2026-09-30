package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Loads orders and moves them from one status to the next.
 *
 * <p>A move is a compare-and-set: it is applied only if the stored order still has the status the
 * caller read, so two staff members acting on the same order cannot both succeed, and staff cannot
 * overwrite a payment that arrived in between. It writes the status, the update time and the
 * delivery quote, and nothing else; the order's lines and prices are never rewritten here.
 */
@Component
@RequiredArgsConstructor
public class OrderStore {

  private static final long ONE_DOCUMENT = 1L;

  private final OrderRepository orders;
  private final OrderMapper mapper;
  private final MongoTemplate mongo;

  /**
   * Loads an order.
   *
   * @param id the order id
   * @return the order
   * @throws NotFoundException if there is no such order
   */
  public Order require(String id) {
    return orders
        .findById(id)
        .map(mapper::toDomain)
        .orElseThrow(() -> new NotFoundException("order.not-found", "No order " + id));
  }

  /**
   * Replaces the status, update time and quote of an order, if it is still as it was read.
   *
   * @param before the order as the caller read it
   * @param after the order as it should be
   * @throws ConflictException if the stored order is no longer in the status {@code before} had
   */
  public void advance(Order before, Order after) {
    final OrderDocument next = mapper.toDocument(after);
    final Query unchanged =
        Query.query(
            Criteria.where("_id").is(before.id().value()).and("status").is(before.status()));
    final Update update =
        new Update()
            .set("status", next.status())
            .set("updatedAt", next.updatedAt())
            .set("quote", next.quote());
    final long changed =
        mongo.updateFirst(unchanged, update, OrderDocument.class).getModifiedCount();
    if (changed != ONE_DOCUMENT) {
      throw new ConflictException(
          "order.changed", "Order " + idOf(before) + " changed while it was being updated");
    }
  }

  private static String idOf(Order order) {
    final OrderId id = order.id();
    return id.value();
  }
}
