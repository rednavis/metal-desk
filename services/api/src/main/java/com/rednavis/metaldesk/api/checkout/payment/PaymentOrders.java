package com.rednavis.metaldesk.api.checkout.payment;

import com.rednavis.metaldesk.api.checkout.CheckoutSession;
import com.rednavis.metaldesk.api.checkout.OrderTransitions;
import com.rednavis.metaldesk.api.checkout.delivery.DeliveryState;
import com.rednavis.metaldesk.api.checkout.delivery.HandoffCustomers;
import com.rednavis.metaldesk.api.persistence.OrderNumberSequence;
import com.rednavis.metaldesk.api.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Creates, finds, saves and cancels the order of a checkout.
 *
 * <p>A new order snapshots the session's lines, which is the moment BRD BR-2's price finality takes
 * effect, carries the delivery quote, takes the next order number (BR-6), and is submitted ({@code
 * CHECKOUT_SUBMITTED}) so that it awaits payment.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrders {

  private final OrderRepository orders;
  private final OrderMapper mapper;
  private final OrderNumberSequence numbers;
  private final HandoffCustomers customers;
  private final Clock clock;

  /**
   * Builds a new order for a session, without saving it.
   *
   * @param session a session with step 1 done and an automatic delivery quote
   * @return the order, {@code AWAITING_PAYMENT}
   */
  public Mono<Order> create(CheckoutSession session) {
    final Instant now = clock.instant();
    return customers
        .resolve(session)
        .zipWith(numbers.next(LocalDate.ofInstant(now, ZoneOffset.UTC)))
        .map(
            pair ->
                OrderTransitions.advance(
                    OrderTransitions.withQuote(
                        Order.created(
                            new OrderId(UUID.randomUUID().toString()),
                            pair.getT2(),
                            pair.getT1(),
                            session.details().orElseThrow().deliveryAddress(),
                            session.lines(),
                            now),
                        session.delivery().flatMap(DeliveryState::quote).orElseThrow()),
                    TransitionTrigger.CHECKOUT_SUBMITTED,
                    now));
  }

  /**
   * Finds an order.
   *
   * @param id the order's id
   * @return the order, or empty
   */
  public Mono<Order> find(OrderId id) {
    return orders.findById(id.value()).map(mapper::toDomain);
  }

  /**
   * Saves an order.
   *
   * @param order the order
   * @return the saved order
   */
  public Mono<Order> save(Order order) {
    return orders.save(mapper.toDocument(order)).map(mapper::toDomain);
  }

  /**
   * Cancels an order the customer will not pay, through the state machine.
   *
   * @param order an order awaiting payment
   * @return the cancelled order, saved
   */
  public Mono<Order> cancel(Order order) {
    return save(
        OrderTransitions.advance(order, TransitionTrigger.CUSTOMER_CANCELLED, clock.instant()));
  }

  /**
   * Records that a payment attempt failed, keeping the order awaiting payment (BRD FR-6.3).
   *
   * @param order an order awaiting payment
   * @return the order after the {@code PAYMENT_FAILED} self-transition, saved; never cancelled
   */
  public Mono<Order> failed(Order order) {
    return save(OrderTransitions.advance(order, TransitionTrigger.PAYMENT_FAILED, clock.instant()));
  }
}
