package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.share.domain.order.Order;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Records the hand-offs after payment, so a test can see them. */
@Primary
@Component
public class RecordingSettlement implements OrderSettlement {

  private final List<Order> settled = new CopyOnWriteArrayList<>();
  private final List<Order> billed = new CopyOnWriteArrayList<>();

  @Override
  public Mono<Void> paid(Order order, Locale locale) {
    return Mono.fromRunnable(() -> settled.add(order));
  }

  @Override
  public Mono<Void> invoiceIssued(Order order, Locale locale) {
    return Mono.fromRunnable(() -> billed.add(order));
  }

  /**
   * The orders reported paid.
   *
   * @return the orders
   */
  public List<Order> paidOrders() {
    return List.copyOf(settled);
  }

  /**
   * The orders reported as invoiced.
   *
   * @return the orders
   */
  public List<Order> invoicedOrders() {
    return List.copyOf(billed);
  }
}
