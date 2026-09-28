package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import reactor.core.publisher.Mono;

/**
 * The port through which {@link InvoiceProvider} reads the order it is invoicing.
 *
 * <p>A payment request carries an order id and an amount, but an invoice needs the order's lines,
 * to split them and to list them. The provider therefore asks for the order by id, from whatever
 * stores orders (persistence, T-030), rather than every payment request carrying a copy of the
 * lines. What it reads are the order's snapshotted lines (BRD BR-2), so the invoice matches what
 * the customer agreed to.
 */
@FunctionalInterface
public interface InvoiceOrders {

  /**
   * Finds an order.
   *
   * @param id the order's identifier
   * @return a {@code Mono} of the order, or empty when there is no such order
   */
  Mono<Order> find(OrderId id);
}
