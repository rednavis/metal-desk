package com.rednavis.metaldesk.api.order;

import com.rednavis.metaldesk.api.auth.AuthenticatedCustomer;
import com.rednavis.metaldesk.api.order.dto.OrderDetailView;
import com.rednavis.metaldesk.api.order.dto.OrderHistoryView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** The order history of the signed-in customer (BRD FR-10.1). Every route needs a token. */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderHistoryController {

  private final OrderHistoryService history;

  /**
   * Lists the caller's orders, newest first.
   *
   * @param customer the signed-in customer
   * @return the history
   */
  @GetMapping
  public Mono<OrderHistoryView> list(@AuthenticationPrincipal AuthenticatedCustomer customer) {
    return history.list(customer);
  }

  /**
   * Shows one of the caller's orders.
   *
   * @param customer the signed-in customer
   * @param number the order number
   * @return the detail, or {@code 404} if it is not the caller's
   */
  @GetMapping("/{number}")
  public Mono<OrderDetailView> detail(
      @AuthenticationPrincipal AuthenticatedCustomer customer, @PathVariable String number) {
    return history.detail(customer, number);
  }
}
