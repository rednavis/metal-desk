package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.order.dto.OrderDetailView;
import com.rednavis.metaldesk.api.order.dto.OrderHistoryView;
import com.rednavis.metaldesk.api.persistence.repository.OrderRepository;
import com.rednavis.metaldesk.api.persistence.repository.ShipmentRepository;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.persistence.document.ShipmentDocument;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.error.NotFoundException;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * The order history of a signed-in customer (BRD FR-10.1): a list and a drill-down, both scoped to
 * the caller.
 *
 * <p>It is a projection of the stored orders. It never prices anything; every amount comes from the
 * order's own snapshot, so a product's price changing later cannot move a total.
 *
 * <p>Another customer's order is {@code 404}, not {@code 403}: a refusal would confirm the order
 * exists.
 */
@Service
@RequiredArgsConstructor
public class OrderHistoryService {

  /** The code of the not-found answer, the same whether the order is missing or someone else's. */
  public static final String NOT_FOUND = "order.not-found";

  private final OrderRepository orders;
  private final ShipmentRepository shipments;
  private final OrderMapper mapper;

  /**
   * The caller's orders, newest first.
   *
   * @param customer the signed-in customer
   * @return the history
   */
  public Mono<OrderHistoryView> list(AuthenticatedCustomer customer) {
    return orders
        .findByCustomerId(customer.id().value())
        .map(mapper::toDomain)
        .collectList()
        .map(
            found ->
                new OrderHistoryView(
                    found.stream()
                        .sorted(
                            Comparator.comparing(Order::createdAt)
                                .thenComparing(order -> order.number().format())
                                .reversed())
                        .map(OrderViews::summary)
                        .toList()));
  }

  /**
   * One of the caller's orders.
   *
   * @param customer the signed-in customer
   * @param number the order number
   * @return the detail
   * @throws NotFoundException {@code order.not-found} if there is no such order, or it is not the
   *     caller's
   */
  public Mono<OrderDetailView> detail(AuthenticatedCustomer customer, String number) {
    return orders
        .findByNumber(number)
        .filter(document -> document.customerId().equals(customer.id().value()))
        .switchIfEmpty(Mono.error(() -> new NotFoundException(NOT_FOUND, "No such order")))
        .flatMap(this::withShipment);
  }

  private Mono<OrderDetailView> withShipment(OrderDocument document) {
    final Order order = mapper.toDomain(document);
    return OrderStatusLabels.shipped(order.status())
        ? shipments
            .findById(document.id())
            .map(OrderHistoryService::details)
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty())
            .map(shipment -> OrderViews.detail(order, shipment))
        : Mono.just(OrderViews.detail(order, Optional.empty()));
  }

  private static ShipmentDetails details(ShipmentDocument document) {
    return new ShipmentDetails(document.carrier(), document.trackingReference());
  }
}
