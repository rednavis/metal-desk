package com.rednavis.metaldesk.payments.invoice;

import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.Optional;
import reactor.core.publisher.Mono;

/** An {@link InvoiceOrders} holding at most one order, for tests. */
public final class InMemoryOrders implements InvoiceOrders {

  private final Optional<Order> held;

  /**
   * Creates the port.
   *
   * @param order the order it holds, or {@code null} for none
   */
  public InMemoryOrders(Order order) {
    this.held = Optional.ofNullable(order);
  }

  @Override
  public Mono<Order> find(OrderId id) {
    return held.filter(order -> order.id().equals(id)).map(Mono::just).orElseGet(Mono::empty);
  }
}
