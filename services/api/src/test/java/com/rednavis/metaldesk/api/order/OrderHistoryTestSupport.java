package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.api.cart.CartTestSupport;
import com.rednavis.metaldesk.api.persistence.AccountFixtures;
import com.rednavis.metaldesk.api.persistence.OrderFixtures;
import com.rednavis.metaldesk.api.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.api.persistence.repository.CustomerRepository;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.api.persistence.repository.ShipmentRepository;
import com.rednavis.metaldesk.share.domain.id.CustomerId;
import com.rednavis.metaldesk.share.domain.id.OrderId;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderNumber;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base of the order history tests: signs customers in and stores orders for them in any status,
 * without driving a checkout.
 */
public class OrderHistoryTestSupport extends CartTestSupport {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();
  private static final LocalDate DAY = LocalDate.of(2031, 1, 1);

  @Autowired protected ShipmentRepository shipments;
  @Autowired private OrderRepository orderStore;
  @Autowired private OrderMapper orderMapper;
  @Autowired private CustomerRepository customerStore;

  /** A signed-in customer. */
  protected record Caller(String token, CustomerId id) {}

  /** Creates the base; subclasses are the tests. */
  protected OrderHistoryTestSupport() {
    super();
  }

  /**
   * Registers, verifies and signs in a new customer.
   *
   * @return the customer and their token
   */
  protected Caller caller() {
    final String email = freshEmail();
    registerAndVerify(email);
    final String token = Objects.requireNonNull(signIn(email, PASSWORD));
    final String id = Objects.requireNonNull(customerStore.findByEmail(email).block()).id();
    return new Caller(token, new CustomerId(id));
  }

  /**
   * Stores an order of two gold and three silver items for a customer, in any status.
   *
   * @param owner the customer
   * @param status the status to store it in
   * @return the order
   */
  protected Order placeOrder(Caller owner, OrderStatus status) {
    final OrderNumber number = new OrderNumber(DAY, SEQUENCE.incrementAndGet());
    final Order created =
        Order.created(
            new OrderId(UUID.randomUUID().toString()),
            number,
            owner.id(),
            AccountFixtures.deliveryAddress(),
            List.of(OrderFixtures.goldLine(), OrderFixtures.silverLine()),
            OrderFixtures.NOW);
    final Order order =
        new Order(
            created.id(),
            created.number(),
            created.customerId(),
            created.deliveryAddress(),
            created.lines(),
            Optional.empty(),
            Optional.empty(),
            status,
            created.createdAt(),
            created.updatedAt());
    orderStore.save(orderMapper.toDocument(order)).block();
    return order;
  }

  /**
   * The rows of a history response.
   *
   * @param listed the response
   * @return the rows
   */
  @SuppressWarnings("unchecked")
  protected List<Map<String, Object>> rows(Called listed) {
    return (List<Map<String, Object>>) listed.body().get("orders");
  }

  /**
   * The total of the first row of a history response.
   *
   * @param listed the response
   * @return the amount as decimal text
   */
  protected String totalOf(Called listed) {
    return (String) ((Map<?, ?>) rows(listed).get(0).get("total")).get("amount");
  }
}
