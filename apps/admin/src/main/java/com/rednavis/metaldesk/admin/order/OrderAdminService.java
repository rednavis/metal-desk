package com.rednavis.metaldesk.admin.order;

import com.rednavis.metaldesk.admin.order.dto.OrderDetailView;
import com.rednavis.metaldesk.admin.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.admin.order.dto.PageView;
import com.rednavis.metaldesk.admin.persistence.CustomerRepository;
import com.rednavis.metaldesk.admin.persistence.OrderRepository;
import com.rednavis.metaldesk.admin.persistence.OrderStore;
import com.rednavis.metaldesk.admin.persistence.ShipmentRepository;
import com.rednavis.metaldesk.persistence.document.OrderDocument;
import com.rednavis.metaldesk.persistence.document.ShipmentDocument;
import com.rednavis.metaldesk.persistence.mapper.OrderMapper;
import com.rednavis.metaldesk.share.domain.order.Order;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import com.rednavis.metaldesk.share.domain.order.OrderTransitions;
import com.rednavis.metaldesk.share.domain.order.TransitionTrigger;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Lists and shows orders, and drives the statuses staff own: {@code PAID -> FULFILLING -> SHIPPED
 * -> DELIVERED}.
 *
 * <p>Each step fires its trigger on the state machine, so a step out of order is refused as a
 * conflict by the machine itself, not by a check of ours, and is applied only if the stored order
 * still has the status that was read ({@link OrderStore#advance}). Entering a shipment is the
 * {@code SHIPPED} trigger: it is only legal from {@code FULFILLING}, so a shipment cannot be
 * entered for an order that is not being fulfilled.
 */
@Service
@RequiredArgsConstructor
public class OrderAdminService {

  private static final int MAX_PAGE_SIZE = 100;

  private final OrderRepository orders;
  private final OrderStore store;
  private final OrderMapper mapper;
  private final CustomerRepository customers;
  private final ShipmentRepository shipments;
  private final Clock clock;

  /**
   * Lists orders, newest first.
   *
   * @param status only orders in this status, or null for all
   * @param page the zero-based page
   * @param size the page size, 1 to 100
   * @return the page
   * @throws ValidationException if the page or size is out of range
   */
  public PageView<OrderSummaryView> list(OrderStatus status, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new ValidationException(
          "paging.invalid", "Page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
    }
    final PageRequest request =
        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    final Page<OrderDocument> found =
        status == null ? orders.findAll(request) : orders.findByStatus(status, request);
    return new PageView<>(
        found.stream().map(mapper::toDomain).map(OrderViews::summary).toList(),
        page,
        size,
        found.getTotalElements());
  }

  /**
   * Shows one order.
   *
   * @param id the order id
   * @return the order in full
   */
  public OrderDetailView detail(String id) {
    return view(store.require(id));
  }

  /**
   * Starts fulfillment of a paid order.
   *
   * @param id the order id
   * @return the order, now {@code FULFILLING}
   */
  public OrderDetailView startFulfillment(String id) {
    return fire(id, TransitionTrigger.FULFILLMENT_STARTED);
  }

  /**
   * Records that a fulfilling order shipped.
   *
   * @param id the order id
   * @param entry the carrier and tracking reference
   * @return the order, now {@code SHIPPED}, with its shipment
   * @throws ConflictException if the order is not being fulfilled
   */
  public OrderDetailView enterShipment(String id, ShipmentEntry entry) {
    if (entry == null) {
      throw new ValidationException("shipment.missing", "A shipment needs a carrier and tracking");
    }
    final Order before = store.require(id);
    final Order after =
        OrderTransitions.advance(before, TransitionTrigger.SHIPPED, clock.instant());
    shipments.save(new ShipmentDocument(id, entry.carrier(), entry.trackingReference()));
    try {
      store.advance(before, after);
    } catch (ConflictException e) {
      shipments.deleteById(id);
      throw e;
    }
    return view(after);
  }

  /**
   * Records that a shipped order was delivered.
   *
   * @param id the order id
   * @return the order, now {@code DELIVERED}
   */
  public OrderDetailView markDelivered(String id) {
    return fire(id, TransitionTrigger.DELIVERED);
  }

  private OrderDetailView fire(String id, TransitionTrigger trigger) {
    final Order before = store.require(id);
    final Order after = OrderTransitions.advance(before, trigger, clock.instant());
    store.advance(before, after);
    return view(after);
  }

  private OrderDetailView view(Order order) {
    return OrderViews.detail(
        order,
        customers.findById(order.customerId().value()).orElse(null),
        shipments.findById(order.id().value()).orElse(null));
  }
}
