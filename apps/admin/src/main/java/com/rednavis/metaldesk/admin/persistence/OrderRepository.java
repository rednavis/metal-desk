package com.rednavis.metaldesk.admin.persistence;

import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/** The orders. Status changes do not go through {@code save}; see {@link OrderStore}. */
public interface OrderRepository extends MongoRepository<OrderDocument, String> {

  /**
   * Pages through the orders in one status.
   *
   * @param status the status
   * @param pageable the page and its order
   * @return the page
   */
  Page<OrderDocument> findByStatus(OrderStatus status, Pageable pageable);
}
