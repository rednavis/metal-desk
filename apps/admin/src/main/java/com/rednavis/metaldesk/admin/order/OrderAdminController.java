package com.rednavis.metaldesk.admin.order;

import com.rednavis.metaldesk.admin.order.dto.OrderDetailView;
import com.rednavis.metaldesk.admin.order.dto.OrderSummaryView;
import com.rednavis.metaldesk.admin.order.dto.PageView;
import com.rednavis.metaldesk.share.domain.order.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order management for staff. The status steps are explicit sub-resources, not a generic "set the
 * status": each one is one trigger of the order state machine, and the machine refuses it with 409
 * if it is not legal from the order's current status.
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAdminController {

  private final OrderAdminService service;

  /**
   * Lists orders, newest first.
   *
   * @param status only orders in this status, or absent for all
   * @param page the zero-based page, default 0
   * @param size the page size, 1 to 100, default 20
   * @return the page
   */
  @GetMapping
  public PageView<OrderSummaryView> list(
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(status, page, size);
  }

  /**
   * Shows one order.
   *
   * @param id the order id
   * @return the order in full
   */
  @GetMapping("/{id}")
  public OrderDetailView detail(@PathVariable String id) {
    return service.detail(id);
  }

  /**
   * Records that an invoice was paid: {@code AWAITING_PAYMENT -> PAID}.
   *
   * @param id the order id
   * @return the order
   */
  @PostMapping("/{id}/payment-received")
  public OrderDetailView markInvoicePaid(@PathVariable String id) {
    return service.markInvoicePaid(id);
  }

  /**
   * Starts fulfillment: {@code PAID -> FULFILLING}.
   *
   * @param id the order id
   * @return the order
   */
  @PostMapping("/{id}/fulfillment")
  public OrderDetailView startFulfillment(@PathVariable String id) {
    return service.startFulfillment(id);
  }

  /**
   * Enters the shipment: {@code FULFILLING -> SHIPPED}.
   *
   * @param id the order id
   * @param entry the carrier and tracking reference
   * @return the order
   */
  @PutMapping("/{id}/shipment")
  public OrderDetailView enterShipment(@PathVariable String id, @RequestBody ShipmentEntry entry) {
    return service.enterShipment(id, entry);
  }

  /**
   * Records delivery: {@code SHIPPED -> DELIVERED}.
   *
   * @param id the order id
   * @return the order
   */
  @PostMapping("/{id}/delivery")
  public OrderDetailView markDelivered(@PathVariable String id) {
    return service.markDelivered(id);
  }
}
